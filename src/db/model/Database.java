package db.model;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class Database implements Serializable {
    // Храним таблицы: Имя таблицы -> Сама таблица
    private final Map<String, Table> tables;

    public Database() {
        this.tables = new HashMap<>();
    }

    public void addTable(Table table) {
        tables.put(table.getName(), table);
    }

    public Table getTable(String name) {
        return tables.get(name);
    }

    public boolean hasTable(String name) {
        return tables.containsKey(name);
    }

    public void dropTable(String name) {
        tables.remove(name);
    }

    public Map<String, Table> getTables() {
        return tables;
    }
}