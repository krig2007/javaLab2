package db.model;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 Корень БД.
 Хранит таблицы в LinkedHashMap, чтобы порядок добавления сохранялся
 (удобно для вывода списка таблиц в GUI).
 Это же дерево целиком уходит в файл database.db.
 */
public class Database implements Serializable {
    private final Map<String, Table> tables = new LinkedHashMap<>();

    public void addTable(Table table) { tables.put(table.getName(), table); }
    public Table getTable(String name) { return tables.get(name); }
    public boolean hasTable(String name) { return tables.containsKey(name); }
    public void dropTable(String name) { tables.remove(name); }
    public Map<String, Table> getTables() { return Collections.unmodifiableMap(tables); }
    public boolean isEmpty() { return tables.isEmpty(); }
}