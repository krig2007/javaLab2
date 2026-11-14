package db.query;

import db.SqlParseException;
import db.model.DataType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Парсер SQL-подобного языка (подмножество). */
public final class QueryParser {
    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.DOTALL;

    private static final Pattern INSERT_PATTERN = Pattern.compile(
            "^\\s*INSERT\\s+INTO\\s+(\\w+)\\s*(\\([^)]*\\))?\\s*VALUES\\s*(.+?)\\s*$", FLAGS);
    private static final Pattern SELECT_PATTERN = Pattern.compile(
            "^\\s*SELECT\\s+(.+?)\\s+FROM\\s+(\\w+)(?:\\s+WHERE\\s+(.+?))?\\s*$", FLAGS);
    private static final Pattern UPDATE_PATTERN = Pattern.compile(
            "^\\s*UPDATE\\s+(\\w+)\\s+SET\\s+(.+?)(?:\\s+WHERE\\s+(.+?))?\\s*$", FLAGS);
    private static final Pattern DELETE_PATTERN = Pattern.compile(
            "^\\s*DELETE\\s+FROM\\s+(\\w+)(?:\\s+WHERE\\s+(.+?))?\\s*$", FLAGS);
    private static final Pattern CREATE_INDEX_PATTERN = Pattern.compile(
            "^\\s*CREATE\\s+INDEX\\s+(\\w+)\\s+ON\\s+(\\w+)\\s*\\(\\s*(\\w+)\\s*\\)\\s*$", FLAGS);
    private static final Pattern CREATE_TABLE_PATTERN = Pattern.compile(
            "^\\s*CREATE\\s+TABLE\\s+(\\w+)\\s*\\((.+)\\)\\s*$", FLAGS);
    private static final Pattern DROP_TABLE_PATTERN = Pattern.compile(
            "^\\s*DROP\\s+TABLE\\s+(\\w+)\\s*$", FLAGS);

    private QueryParser() {}

    /** Разбирает SQL-строку в {@link Query}. */
    public static Query parse(String sql) throws SqlParseException {
        if (sql == null || sql.trim().isEmpty())
            throw new SqlParseException("Пустой запрос");
        sql = sql.trim();
        if (sql.endsWith(";")) sql = sql.substring(0, sql.length() - 1);

        Matcher m = INSERT_PATTERN.matcher(sql);
        if (m.matches()) return parseInsert(m);

        m = SELECT_PATTERN.matcher(sql);
        if (m.matches()) return parseSelect(m);

        m = UPDATE_PATTERN.matcher(sql);
        if (m.matches()) return parseUpdate(m);

        m = DELETE_PATTERN.matcher(sql);
        if (m.matches()) return parseDelete(m);

        m = CREATE_INDEX_PATTERN.matcher(sql);
        if (m.matches()) return parseCreateIndex(m);

        m = CREATE_TABLE_PATTERN.matcher(sql);
        if (m.matches()) return parseCreateTable(m);

        m = DROP_TABLE_PATTERN.matcher(sql);
        if (m.matches()) return parseDropTable(m);

        throw new SqlParseException("Не удалось распознать команду: " + sql);
    }

    private static Query parseInsert(Matcher m) throws SqlParseException {
        Query q = new Query();
        q.type = Query.Type.INSERT;
        q.tableName = m.group(1);
        String colsPart = m.group(2);
        if (colsPart != null) {
            q.insertColumns = SqlUtils.splitTopLevel(SqlUtils.stripParens(colsPart), ',');
            if (q.insertColumns.isEmpty())
                throw new SqlParseException("Пустой список колонок");
        }
        q.insertValues = new ArrayList<>();
        for (String rowGroup : SqlUtils.splitTopLevel(m.group(3), ',')) {
            List<String> values = SqlUtils.splitTopLevel(SqlUtils.stripParens(rowGroup), ',');
            q.insertValues.add(values);
        }
        if (q.insertValues.isEmpty())
            throw new SqlParseException("VALUES не содержит строк");
        return q;
    }

    private static Query parseSelect(Matcher m) {
        Query q = new Query();
        q.type = Query.Type.SELECT;
        String colsPart = m.group(1).trim();
        if ("*".equals(colsPart)) {
            q.selectColumns = null;
        } else {
            q.selectColumns = SqlUtils.splitTopLevel(colsPart, ',');
        }
        q.tableName = m.group(2);
        q.whereClause = m.group(3);
        return q;
    }

    private static Query parseUpdate(Matcher m) throws SqlParseException {
        Query q = new Query();
        q.type = Query.Type.UPDATE;
        q.tableName = m.group(1);
        q.setClauses = new LinkedHashMap<>();
        for (String clause : SqlUtils.splitTopLevel(m.group(2), ',')) {
            int eq = clause.indexOf('=');
            if (eq < 0) throw new SqlParseException("Неверный SET-фрагмент: " + clause);
            String col = clause.substring(0, eq).trim();
            String val = clause.substring(eq + 1).trim();
            q.setClauses.put(col, val);
        }
        q.whereClause = m.group(3);
        return q;
    }

    private static Query parseDelete(Matcher m) {
        Query q = new Query();
        q.type = Query.Type.DELETE;
        q.tableName = m.group(1);
        q.whereClause = m.group(2);
        return q;
    }

    private static Query parseCreateIndex(Matcher m) {
        Query q = new Query();
        q.type = Query.Type.CREATE_INDEX;
        q.indexName = m.group(1);
        q.tableName = m.group(2);
        q.indexColumn = m.group(3);
        return q;
    }

    private static Query parseCreateTable(Matcher m) throws SqlParseException {
        Query q = new Query();
        q.type = Query.Type.CREATE_TABLE;
        q.tableName = m.group(1);
        q.createSchema = new LinkedHashMap<>();
        q.primaryKeyColumn = null;

        for (String part : SqlUtils.splitTopLevel(m.group(2), ',')) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) continue;
            if (trimmed.toUpperCase().startsWith("PRIMARY KEY")) {
                String inner = trimmed.substring("PRIMARY KEY".length()).trim();
                q.primaryKeyColumn = SqlUtils.stripParens(inner).trim();
            } else {
                String[] tokens = trimmed.split("\\s+");
                if (tokens.length < 2)
                    throw new SqlParseException("Неверное описание колонки: " + trimmed);
                DataType dt = SqlUtils.parseDataType(tokens[1]);
                q.createSchema.put(tokens[0], dt);
            }
        }
        if (q.createSchema.isEmpty())
            throw new SqlParseException("CREATE TABLE без колонок");
        if (q.primaryKeyColumn == null)
            q.primaryKeyColumn = q.createSchema.keySet().iterator().next();
        if (!q.createSchema.containsKey(q.primaryKeyColumn))
            throw new SqlParseException("PRIMARY KEY указывает на несуществующую колонку: "
                    + q.primaryKeyColumn);
        return q;
    }

    private static Query parseDropTable(Matcher m) {
        Query q = new Query();
        q.type = Query.Type.DROP_TABLE;
        q.tableName = m.group(1);
        return q;
    }
}