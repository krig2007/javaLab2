package db;

/** Таблица не найдена. */
public class TableNotFoundException extends DbException {
    public TableNotFoundException(String name) { super("Таблица '" + name + "' не существует"); }
}