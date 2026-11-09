package db.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Index implements Serializable {
    private final String columnName;
    // Ключ: значение колонки (например, "Toyota"). Значение: список строк, где это встречается.
    private final Map<Object, List<Row>> indexMap = new HashMap<>();

    public Index(String columnName) {
        this.columnName = columnName;
    }

    public String getColumnName() { return columnName; }

    public void add(Row row) {
        Object key = row.get(columnName).getData();
        indexMap.computeIfAbsent(key, k -> new ArrayList<>()).add(row);
    }

    public List<Row> getRows(Object value) {
        return indexMap.getOrDefault(value, new ArrayList<>());
    }
}