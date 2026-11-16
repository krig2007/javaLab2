package db.query;

import db.SqlParseException;
import db.model.DataType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
Парсер SQL-подобного языка.
Разбирает запросы с помощью регулярных выражений.
Поддерживаются INSERT, SELECT, UPDATE, DELETE, CREATE INDEX, CREATE TABLE и DROP TABLE.
Результат разбора сохраняется в объект Query.
 */
public final class QueryParser {
    // DOTALL позволяет обрабатывать запросы, записанные в несколько строк
    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.DOTALL;

    /*
     INSERT INTO <table> [(col1, col2, ...)] VALUES (v1, v2, ...) [, (v1, v2, ...)]
     Группа 1 - имя таблицы.
     Группа 2 - список колонок, если он указан.
     Группа 3 - значения для одной или нескольких строк.
     */
    private static final Pattern INSERT_PATTERN = Pattern.compile(
            "^\\s*INSERT\\s+INTO\\s+(\\w+)\\s*(\\([^)]*\\))?\\s*VALUES\\s*(.+?)\\s*$", FLAGS);

    /*
     SELECT <cols|*> FROM <table> [WHERE <cond>]
     Группа 1 - список колонок или *.
     Группа 2 - имя таблицы.
     Группа 3 - условие WHERE, если оно есть.
     */
    private static final Pattern SELECT_PATTERN = Pattern.compile(
            "^\\s*SELECT\\s+(.+?)\\s+FROM\\s+(\\w+)(?:\\s+WHERE\\s+(.+?))?\\s*$", FLAGS);

    /*
    UPDATE <table> SET <col1=val1, col2=val2, ...> [WHERE <cond>]
    Группа 1 - имя таблицы, группа 2 - новые значения, группа 3 - условие WHERE.
     */
    private static final Pattern UPDATE_PATTERN = Pattern.compile(
            "^\\s*UPDATE\\s+(\\w+)\\s+SET\\s+(.+?)(?:\\s+WHERE\\s+(.+?))?\\s*$", FLAGS);

    /*
     DELETE FROM <table> [WHERE <cond>]
     */
    private static final Pattern DELETE_PATTERN = Pattern.compile(
            "^\\s*DELETE\\s+FROM\\s+(\\w+)(?:\\s+WHERE\\s+(.+?))?\\s*$", FLAGS);

    /*
    CREATE INDEX <idx_name> ON <table> (<col>)
     */
    private static final Pattern CREATE_INDEX_PATTERN = Pattern.compile(
            "^\\s*CREATE\\s+INDEX\\s+(\\w+)\\s+ON\\s+(\\w+)\\s*\\(\\s*(\\w+)\\s*\\)\\s*$", FLAGS);

    /*
     CREATE TABLE <name> (<col TYPE, ..., PRIMARY KEY (col)>)
     */
    private static final Pattern CREATE_TABLE_PATTERN = Pattern.compile(
            "^\\s*CREATE\\s+TABLE\\s+(\\w+)\\s*\\((.+)\\)\\s*$", FLAGS);

    /*
     DROP TABLE <name>
     */
    private static final Pattern DROP_TABLE_PATTERN = Pattern.compile(
            "^\\s*DROP\\s+TABLE\\s+(\\w+)\\s*$", FLAGS);

    private QueryParser() {}

    /*
    Проверяем запрос по очереди на соответствие каждому шаблону.
    Если найден подходящий, передаём его соответствующему методу.
    Если ни один шаблон не подошёл, выдаём ошибку разбора.
     */
    public static Query parse(String sql) throws SqlParseException {
        if (sql == null || sql.trim().isEmpty())
            throw new SqlParseException("Пустой запрос");

        // Убираем пробелы по краям и ";" в конце
        sql = sql.trim();
        if (sql.endsWith(";")) sql = sql.substring(0, sql.length() - 1);

        // Проверяем, какой команде соответствует запрос
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

        // Если запрос не подошёл ни под один шаблон
        throw new SqlParseException("Не удалось распознать команду: " + sql);
    }

    /*
    Разбираем INSERT.
    Сначала получаем список колонок, если он указан.
    Затем разделяем VALUES на отдельные строки и получаем значения для каждой из них.
     */
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

    /*
     Разбираем SELECT.
     Если указано *, список колонок оставляем пустым (null).
     При выполнении запроса вместо него будут использованы все колонки таблицы.
     */
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

    /*
     Разбираем UPDATE.
     В SET может быть несколько присваиваний, разделённых запятыми.
     Например: "price = 30000, is_new = FALSE".
     Сохраняем их в LinkedHashMap, чтобы не менять порядок.
     */
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

    /*
    Разбираем DELETE.
    Условие WHERE необязательно. Если его нет, будут удалены все записи таблицы.
     */
    private static Query parseDelete(Matcher m) {
        Query q = new Query();
        q.type = Query.Type.DELETE;
        q.tableName = m.group(1);
        q.whereClause = m.group(2);
        return q;
    }

    /*
     Разбираем CREATE INDEX.
     Сохраняем имя индекса и колонку, для которой он будет создан.
     */
    private static Query parseCreateIndex(Matcher m) {
        Query q = new Query();
        q.type = Query.Type.CREATE_INDEX;
        q.indexName = m.group(1);
        q.tableName = m.group(2);
        q.indexColumn = m.group(3);
        return q;
    }

    /*
    Разбираем CREATE TABLE.
    Получаем список колонок и их типов, а также первичный и внешние ключи.
    NOT NULL, DEFAULT и UNIQUE пока не поддерживаются.
    Если первичный ключ не указан, им становится первая колонка.
     */
    private static Query parseCreateTable(Matcher m) throws SqlParseException {
        Query q = new Query();
        q.type = Query.Type.CREATE_TABLE;
        q.tableName = m.group(1);
        q.createSchema = new LinkedHashMap<>();
        q.primaryKeyColumn = null;
        q.foreignKeys = new LinkedHashMap<>(); // Создаём список внешних ключей

        for (String part : SqlUtils.splitTopLevel(m.group(2), ',')) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) continue;

            if (trimmed.toUpperCase().startsWith("PRIMARY KEY")) {
                String inner = trimmed.substring("PRIMARY KEY".length()).trim();
                q.primaryKeyColumn = SqlUtils.stripParens(inner).trim();
            }
            // Обрабатываем объявление внешнего ключа
            else if (trimmed.toUpperCase().startsWith("FOREIGN KEY")) {
                // Формат: FOREIGN KEY (car_id) REFERENCES cars(id)
                Pattern fkPattern = Pattern.compile(
                        "FOREIGN\\s+KEY\\s*\\(\\s*(\\w+)\\s*\\)\\s+REFERENCES\\s+(\\w+)\\s*\\(\\s*(\\w+)\\s*\\)",
                        Pattern.CASE_INSENSITIVE
                );
                Matcher fkMatcher = fkPattern.matcher(trimmed);
                if (fkMatcher.matches()) {
                    String fkCol = fkMatcher.group(1);   // car_id
                    String refTable = fkMatcher.group(2); // cars
                    String refCol = fkMatcher.group(3);   // id
                    q.foreignKeys.put(fkCol, refTable + "." + refCol);
                } else {
                    throw new SqlParseException("Неверный синтаксис FOREIGN KEY: " + trimmed);
                }
            }
            else {
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

    /*
     Разбираем DROP TABLE и получаем имя таблицы, которую нужно удалить.
     */
    private static Query parseDropTable(Matcher m) {
        Query q = new Query();
        q.type = Query.Type.DROP_TABLE;
        q.tableName = m.group(1);
        return q;
    }
}
