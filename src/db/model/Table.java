package db.model;

import db.DbException;
import db.DuplicateKeyException;

import java.io.Serializable;
import java.util.*;

/**
 * Таблица: схема + строки + первичный ключ + набор индексов.
 * <p>Инварианты:</p>
 * <ul>
 *   <li>первичный ключ уникален;</li>
 *   <li>все индексы консистентны с содержимым {@code rows}.</li>
 * </ul>
 */
public class Table implements Serializable {
    private final String name;
    private final LinkedHashMap<String, DataType> schema;
    private final String primaryKeyColumn;
    private final List<Row> rows = new ArrayList<>();
    private final Set<Object> primaryKeys = new HashSet<>();
    private final Map<String, Index> indexes = new HashMap<>();

    public Table(String name, LinkedHashMap<String, DataType> schema, String primaryKeyColumn) {
        this.name = name;
        this.schema = schema;
        this.primaryKeyColumn = primaryKeyColumn;
    }

    public String getName() { return name; }
    public Map<String, DataType> getSchema() { return Collections.unmodifiableMap(schema); }
    public List<Row> getRows() { return Collections.unmodifiableList(rows); }
    public String getPrimaryKeyColumn() { return primaryKeyColumn; }
    public Map<String, Index> getIndexes() { return Collections.unmodifiableMap(indexes); }

    /**
     * Вставляет строку с проверкой уникальности PK и типов значений.
     *
     * @throws DbException если PK отсутствует / дубликат / тип не совпадает
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

    /**
     * Обновляет одну колонку в строке с поддержкой индексов и уникальности PK.
     *
     * @throws DbException если колонка не найдена / тип неверен / дубликат PK
     */
    public void update(Row row, String column, Value newValue) throws DbException {
        DataType expected = schema.get(column);
        if (expected == null) throw new DbException("Колонка не найдена: " + column);
        if (newValue != null && newValue.getType() != expected) {
            throw new DbException("Неверный тип для колонки " + column);
        }
        Value oldValue = row.get(column);

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

        Index idx = indexes.get(column);
        if (idx != null) {
            Object oldKey = null;
            if (oldValue != null) {
                oldKey = oldValue.getData();
            }
            idx.remove(row, oldKey);

            Object newKey = null;
            if (newValue != null) {
                newKey = newValue.getData();
            }
            idx.add(row, newKey);
        }

        row.set(column, newValue);
    }

    /** Удаляет строку из таблицы и из всех индексов. */
    public void delete(Row row) {
        Value pk = row.get(primaryKeyColumn);
        if (pk != null) primaryKeys.remove(pk.getData());
        for (Index idx : indexes.values()) {
            Value v = row.get(idx.getColumnName());
            idx.remove(row, v == null ? null : v.getData());
        }
        rows.remove(row);
    }

    /** Создаёт индекс по колонке. Если уже существует — возвращает его. */
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
}