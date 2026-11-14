package db.model;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Строка таблицы: колонка → значение. Порядок колонок сохраняется. */
public class Row implements Serializable {
    private final Map<String, Value> data = new LinkedHashMap<>();

    public void set(String columnName, Value value) { data.put(columnName, value); }

    /** Возвращает значение или {@code null}, если колонка не задана. */
    public Value get(String columnName) { return data.get(columnName); }

    @Override
    public String toString() { return data.toString(); }
}