package db.model;

import java.io.Serializable;
import java.util.*;

/**
 * Хеш-индекс по одной колонке: значение колонки → список строк.
 * <p>Поддерживается в актуальном состоянии через {@link Table}.</p>
 */
public class Index implements Serializable {
    private final String columnName;
    private final Map<Object, List<Row>> indexMap = new HashMap<>();

    public Index(String columnName) { this.columnName = columnName; }

    /** Добавляет строку, ключ берётся из самой строки. */
    public void add(Row row) {
        Value v = row.get(columnName);
        if (v != null && v.getData() != null) add(row, v.getData());
    }

    /** Добавляет строку с заданным ключом. NULL-ключи не индексируются. */
    public void add(Row row, Object key) {
        if (key == null) return;
        indexMap.computeIfAbsent(key, k -> new ArrayList<>()).add(row);
    }

    /** Удаляет строку по ключу. */
    public void remove(Row row, Object key) {
        if (key == null) return;
        List<Row> list = indexMap.get(key);
        if (list != null) {
            list.remove(row);
            if (list.isEmpty()) indexMap.remove(key);
        }
    }

    /** Возвращает строки с данным ключом (может быть пустой список). */
    public List<Row> lookup(Object key) {
        return indexMap.getOrDefault(key, Collections.emptyList());
    }
}