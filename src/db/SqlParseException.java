package db;

/**
 Ошибка разбора SQL-запроса:
 синтаксис, неизвестный тип, нет кавычек и т.п.
 Бросается парсером.
 */
public class SqlParseException extends DbException {
    public SqlParseException(String message) { super(message); }
}