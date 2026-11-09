package db.model;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

public class Row implements Serializable {
    // LinkedHashMap сохраняет порядок добавления колонок (важно для вывода таблицы на экран)
    private final Map<String, Value> data = new LinkedHashMap<>();

    public void set(String columnName, Value value) {
        data.put(columnName, value);
    }

    public Value get(String columnName) {
        return data.get(columnName);
    }

    public Map<String, Value> getData() {
        return data;
    }

    @Override
    public String toString() {
        return data.toString();
    }
}
