package db.model;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 Одна строка таблицы.
 Внутри хранится LinkedHashMap, поэтому порядок колонок сохраняется:
 как объявили в схеме, так и будет выводиться.
 */
public class Row implements Serializable {
    private final Map<String, Value> data = new LinkedHashMap<>();

    public void set(String columnName, Value value) { data.put(columnName, value); }

    /*
     Возвращаем значение колонки.
     Если колонка не была заполнена (например, INSERT (id, brand) без остальных) — вернётся null.
     */
    public Value get(String columnName) { return data.get(columnName); }

    @Override
    public String toString() { return data.toString(); }
}