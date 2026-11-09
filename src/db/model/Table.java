package db.model;

import java.io.Serializable;
import java.util.*;

public class Table implements Serializable {
    private final String name;
    // Схема таблицы: имя колонки -> тип данных
    private final LinkedHashMap<String, DataType> schema;
    private final List<Row> rows;
    // Для быстрой проверки уникальности первичного ключа (Лекция 8, Set)
    private final Set<Object> primaryKeys;
    private final String primaryKeyColumn;

    public Table(String name, LinkedHashMap<String, DataType> schema, String primaryKeyColumn) {
        this.name = name;
        this.schema = schema;
        this.primaryKeyColumn = primaryKeyColumn;
        this.rows = new ArrayList<>();
        this.primaryKeys = new HashSet<>();
    }

    public String getName() {
        return name;
    }

    public LinkedHashMap<String, DataType> getSchema() {
        return schema;
    }

    public List<Row> getRows() {
        return rows;
    }

    public String getPrimaryKeyColumn() {
        return primaryKeyColumn;
    }

    // Вставка строки с проверкой уникальности первичного ключа
    public void insert(Row row) throws Exception {
        Value pkValue = row.get(primaryKeyColumn);
        if (pkValue == null) {
            throw new Exception("Отсутствует значение первичного ключа: " + primaryKeyColumn);
        }

        Object rawPk = pkValue.getData();
        if (primaryKeys.contains(rawPk)) {
            throw new Exception("Нарушение уникальности первичного ключа: " + rawPk);
        }

        primaryKeys.add(rawPk);
        rows.add(row);
    }
    private final Map<String, Index> indexes = new HashMap<>();

    public void createIndex(String columnName) {
        Index idx = new Index(columnName);
        for (Row row : rows) {
            idx.add(row);
        }
        indexes.put(columnName, idx);
        System.out.println("Индекс создан для колонки: " + columnName);
    }

    public Index getIndex(String columnName) {
        return indexes.get(columnName);
    }
}