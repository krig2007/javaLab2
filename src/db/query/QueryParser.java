package db.query;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class QueryParser {
    // Регулярные выражения для разных команд
    private static final Pattern INSERT_PATTERN = Pattern.compile(
            "INSERT\\s+INTO\\s+(\\w+)\\s+VALUES\\s*\\((.*?)\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SELECT_PATTERN = Pattern.compile(
            "SELECT\\s+(.*?)\\s+FROM\\s+(\\w+)(?:\\s+WHERE\\s+(.*))?", Pattern.CASE_INSENSITIVE);
    private static final Pattern UPDATE_PATTERN = Pattern.compile(
            "UPDATE\\s+(\\w+)\\s+SET\\s+(.*?)\\s+WHERE\\s+(.*)", Pattern.CASE_INSENSITIVE);
    private static final Pattern DELETE_PATTERN = Pattern.compile(
            "DELETE\\s+FROM\\s+(\\w+)\\s+WHERE\\s+(.*)", Pattern.CASE_INSENSITIVE);

    // Простой класс для результата парсинга
    public static class Query {
        public String type; // INSERT, SELECT, UPDATE, DELETE
        public String tableName;
        public String columnsOrValues;
        public String whereClause;
    }

    public static Query parse(String sql) {
        Query q = new Query();
        // Убираем точку с запятой в конце (если есть) и лишние пробелы
        if (sql.endsWith(";")) {
            sql = sql.substring(0, sql.length() - 1);
        }
        sql = sql.trim();

        Matcher m = INSERT_PATTERN.matcher(sql);
        if (m.matches()) {
            q.type = "INSERT";
            q.tableName = m.group(1);
            q.columnsOrValues = m.group(2);
            return q;
        }

        m = SELECT_PATTERN.matcher(sql);
        if (m.matches()) {
            q.type = "SELECT";
            q.columnsOrValues = m.group(1);
            q.tableName = m.group(2);
            q.whereClause = m.group(3) != null ? m.group(3).trim() : null;
            return q;
        }

        m = UPDATE_PATTERN.matcher(sql);
        if (m.matches()) {
            q.type = "UPDATE";
            q.tableName = m.group(1);
            q.columnsOrValues = m.group(2);
            q.whereClause = m.group(3);
            return q;
        }

        m = DELETE_PATTERN.matcher(sql);
        if (m.matches()) {
            q.type = "DELETE";
            q.tableName = m.group(1);
            q.whereClause = m.group(2);
            return q;
        }

        return null; // Если не распознали
    }
}