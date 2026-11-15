package db;

/**
 Таблица, к которой обратились, не существует.
 */
public class TableNotFoundException extends DbException {
    public TableNotFoundException(String name) { super("Таблица '" + name + "' не существует"); }
}