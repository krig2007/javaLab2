package db.query;

import db.model.Row;

import java.util.List;

/**
 Результат выполнения запроса.
 Используется GUI для отображения таблицы или сообщения.
   kind     — что это: текст, таблица или ошибка,
   message  — текст,
   columns  — имена колонок (для таблицы),
   rows     — строки (для таблицы),
   modified — true, если запрос изменил БД (нужно сохранить на диск).
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

    // Фабричный метод для текстового ответа (INSERT/UPDATE/DELETE)
    public static QueryResult message(String msg, boolean modified) {
        return new QueryResult(Kind.MESSAGE, msg, null, null, modified);
    }

    // Фабричный метод для табличного ответа (SELECT)
    public static QueryResult table(List<String> columns, List<Row> rows, String msg) {
        return new QueryResult(Kind.TABLE, msg, columns, rows, false);
    }

    // Фабричный метод для ошибки
    public static QueryResult error(String msg) {
        return new QueryResult(Kind.ERROR, msg, null, null, false);
    }
}