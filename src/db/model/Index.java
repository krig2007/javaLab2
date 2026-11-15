package db.model;

import java.io.Serializable;
import java.util.*;

/**
 Вторичный индекс по одной колонке.
 Структура: значение колонки -> список строк с этим значением.
 Живёт в памяти, но целиком сериализуется вместе с таблицей,
 поэтому после перезапуска восстанавливается автоматически.
 */
public class Index implements Serializable {
    private final String columnName;

    /*
     Ключ — значение колонки (Integer, String, Double, Boolean или LocalDate).
     Значение — все строки с этим ключом.
     */
    private final Map<Object, List<Row>> indexMap = new HashMap<>();

    public Index(String columnName) {
        this.columnName = columnName;
    }

    public String getColumnName() {
        return columnName;
    }

    /*
     Добавляем строку. Ключ берём из самой строки.
     NULL-значения не индексируются.
     */
    public void add(Row row) {
        Value v = row.get(columnName);
        if (v != null && v.getData() != null) {
            add(row, v.getData());
        }
    }

    /*
     Добавляем строку с заданным ключом (например, при обновлении поля).
     */
    public void add(Row row, Object key) {
        if (key == null) return;
        indexMap.computeIfAbsent(key, k -> new ArrayList<>()).add(row);
    }

    /*
     Удаляем строку из цепочки по ключу.
     Если цепочка стала пустой — убираем сам ключ, чтобы не копить мусор.
     */
    public void remove(Row row, Object key) {
        if (key == null) return;
        List<Row> list = indexMap.get(key);
        if (list != null) {
            list.remove(row);
            if (list.isEmpty()) {
                indexMap.remove(key);
            }
        }
    }

    /*
     Возвращаем все строки с данным ключом.
     Если ключа нет — пустой список (а не null).
     */
    public List<Row> lookup(Object key) {
        return indexMap.getOrDefault(key, Collections.emptyList());
    }
}