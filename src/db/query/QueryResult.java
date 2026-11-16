package db.query;

import db.model.Row;

import java.util.List;

/**
Хранит результат выполнения запроса.
Используется в GUI для вывода сообщений, ошибок или таблиц.
kind - тип результата,
message - текст сообщения,
columns - названия колонок,
rows - полученные строки,
modified - показывает, изменились ли данные в БД.
 */
public class QueryResult {
    public enum Kind { MESSAGE, TABLE, ERROR }

    public final Kind kind;
    public final String message;
    public final List<String> columns;
    public final List<Row> rows;
    public final boolean modified;

    private QueryResult(Kind kind, String message, List<String> columns,
                        List<Row> rows, boolean modified) {
        this.kind = kind;
        this.message = message;
        this.columns = columns;
        this.rows = rows;
        this.modified = modified;
    }

    // Создаём результат с обычным текстовым сообщением
    public static QueryResult message(String msg, boolean modified) {
        return new QueryResult(Kind.MESSAGE, msg, null, null, modified);
    }

    // Создаём результат с данными таблицы
    public static QueryResult table(List<String> columns, List<Row> rows, String msg) {
        return new QueryResult(Kind.TABLE, msg, columns, rows, false);
    }

    // Создаём результат с сообщением об ошибке
    public static QueryResult error(String msg) {
        return new QueryResult(Kind.ERROR, msg, null, null, false);
    }
}
