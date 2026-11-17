package db.model;

import db.DbException;
import db.DuplicateKeyException;

import java.io.Serializable;
import java.util.*;

/**
 Одна таблица БД.
 Хранит:
   - схему (имя колонки -> тип),
   - сами строки,
   - первичные ключи,
   - вторичные индексы по колонкам.
 Все модификации данных идут только через методы этой таблицы,
 поэтому индексы и первичные ключи всегда остаются достоверными.
 */
public class Table implements Serializable {
    private final String name;
    private final LinkedHashMap<String, DataType> schema;
    private final String primaryKeyColumn;
    private final List<Row> rows = new ArrayList<>();
    private final Set<Object> primaryKeys = new HashSet<>();
    private final Map<String, Index> indexes = new HashMap<>();
    private final Map<String, String> foreignKeys = new HashMap<>();

    public Table(String name, LinkedHashMap<String, DataType> schema, String primaryKeyColumn) {
        this.name = name;
        this.schema = schema;
        this.primaryKeyColumn = primaryKeyColumn;
    }

    public String getName() { return name; }
    public Map<String, DataType> getSchema() { return Collections.unmodifiableMap(schema); }
    public List<Row> getRows() { return Collections.unmodifiableList(rows); }

    /*
     Вставка строки.
     1. Проверяем, что PK (первичный ключ) заполнен и уникален.
     2. Проверяем, что тип каждого значения совпадает со схемой.
     3. Добавляем строку в список и во все существующие индексы.
     */
    public void insert(Row row) throws DbException {
        Value pkValue = row.get(primaryKeyColumn);
        if (pkValue == null || pkValue.getData() == null) {
            throw new DbException("Отсутствует значение первичного ключа: " + primaryKeyColumn);
        }
        Object rawPk = pkValue.getData();
        if (primaryKeys.contains(rawPk)) {
            throw new DuplicateKeyException("Нарушение уникальности первичного ключа: " + rawPk);
        }
        for (Map.Entry<String, DataType> e : schema.entrySet()) {
            Value v = row.get(e.getKey());
            if (v != null && v.getType() != e.getValue()) {
                throw new DbException("Неверный тип для колонки '" + e.getKey()
                        + "': ожидался " + e.getValue() + ", получен " + v.getType());
            }
        }
        primaryKeys.add(rawPk);
        rows.add(row);
        for (Index idx : indexes.values()) idx.add(row);
    }

    /*
     Обновление одного поля в строке.
     Отдельно обрабатываем случай, когда меняется первичный ключ:
     нужно снять старый ключ из HashSet и проверить новый на уникальность.
     Также обновляем индексы: удаляем строку по старому значению и добавляем по новому.
     */
    public void update(Row row, String column, Value newValue) throws DbException {
        DataType expected = schema.get(column);
        if (expected == null) throw new DbException("Колонка не найдена: " + column);
        if (newValue != null && newValue.getType() != expected) {
            throw new DbException("Неверный тип для колонки " + column);
        }
        Value oldValue = row.get(column);

        // 1. Если меняется PK — обновляем множество ключей
        if (column.equals(primaryKeyColumn)) {
            Object oldPk = oldValue == null ? null : oldValue.getData();
            Object newPk = newValue == null ? null : newValue.getData();
            if (!Objects.equals(oldPk, newPk)) {
                if (newPk == null) throw new DbException("Первичный ключ не может быть NULL");
                if (primaryKeys.contains(newPk))
                    throw new DuplicateKeyException("Нарушение уникальности первичного ключа: " + newPk);
                primaryKeys.remove(oldPk);
                primaryKeys.add(newPk);
            }
        }

        // 2. Если по этой колонке есть индекс — перекладываем строку
        Index idx = indexes.get(column);
        if (idx != null) {
            if (oldValue != null) {
                idx.remove(row, oldValue.getData());
            }
            if (newValue != null) {
                idx.add(row, newValue.getData());
            }
        }

        // 3. Записываем новое значение в строку
        row.set(column, newValue);
    }

    /*
     Удаление строки.
     Запись убирается из списка, из множества ключей и из всех индексов.
     */
    public void delete(Row row) {
        Value pk = row.get(primaryKeyColumn);
        if (pk != null) primaryKeys.remove(pk.getData());
        for (Index idx : indexes.values()) {
            Value v = row.get(idx.getColumnName());
            if (v != null && v.getData() != null) {
                idx.remove(row, v.getData());
            }
        }
        rows.remove(row);
    }

    /*
     Создание индекса.
     Проходим по всем уже существующим строкам и добавляем их в новый индекс.
     Если индекс уже есть — возвращаем его.
     */
    public Index createIndex(String columnName) throws DbException {
        if (!schema.containsKey(columnName))
            throw new DbException("Колонка не найдена: " + columnName);
        Index existing = indexes.get(columnName);
        if (existing != null) return existing;
        Index idx = new Index(columnName);
        for (Row row : rows) idx.add(row);
        indexes.put(columnName, idx);
        return idx;
    }

    public Index getIndex(String columnName) { return indexes.get(columnName); }
    public boolean hasIndex(String columnName) { return indexes.containsKey(columnName); }

    /* Добавляет правило внешнего ключа: колонка -> целевая таблица.колонка */
    public void addForeignKey(String column, String targetTableAndColumn) {
        foreignKeys.put(column, targetTableAndColumn);
    }

    /* Возвращает карту внешних ключей (только для чтения). */
    public Map<String, String> getForeignKeys() {
        return Collections.unmodifiableMap(foreignKeys);
    }
}