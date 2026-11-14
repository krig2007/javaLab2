package db;

/** Ошибка разбора SQL-запроса. */
public class SqlParseException extends DbException {
    public SqlParseException(String message) { super(message); }
}