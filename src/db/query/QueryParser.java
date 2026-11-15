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
 Разбираем строку регулярками — этого достаточно для минимального подмножества
 INSERT/SELECT/UPDATE/DELETE + CREATE INDEX/TABLE + DROP TABLE.
 На выходе получаем Query — DTO с полями под конкретный тип запроса.
 */
public final class QueryParser {
    // DOTALL, чтобы точка в регулярках ловила переносы строк (многострочные запросы)
    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.DOTALL;

    /*
     INSERT INTO <table> [(col1, col2, ...)] VALUES (v1, v2, ...) [, (v1, v2, ...)]
     Группа 1 — имя таблицы.
     Группа 2 — необязательный список колонок в скобках.
     Группа 3 — всё, что идёт после VALUES (одна или несколько групп значений).
     */
    private static final Pattern INSERT_PATTERN = Pattern.compile(
            "^\\s*INSERT\\s+INTO\\s+(\\w+)\\s*(\\([^)]*\\))?\\s*VALUES\\s*(.+?)\\s*$", FLAGS);

    /*
     SELECT <cols|*> FROM <table> [WHERE <cond>]
     Группа 1 — список колонок (или *).
     Группа 2 — имя таблицы.
     Группа 3 — условие (может отсутствовать).
     */
    private static final Pattern SELECT_PATTERN = Pattern.compile(
            "^\\s*SELECT\\s+(.+?)\\s+FROM\\s+(\\w+)(?:\\s+WHERE\\s+(.+?))?\\s*$", FLAGS);

    /*
     UPDATE <table> SET <col1=val1, col2=val2, ...> [WHERE <cond>]
     Группа 1 — таблица, 2 — присваивания, 3 — WHERE.
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
     Точка входа: прогоняем строку по всем шаблонам по очереди.
     Первый совпавший — наш. Если ни один не подошёл — SqlParseException.
     */
    public static Query parse(String sql) throws SqlParseException {
        if (sql == null || sql.trim().isEmpty())
            throw new SqlParseException("Пустой запрос");

        // 1. Убираем хвостовую ; и лишние пробелы
        sql = sql.trim();
        if (sql.endsWith(";")) sql = sql.substring(0, sql.length() - 1);

        // 2. Пробуем каждый шаблон по очереди
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

        // 3. Ничего не подошло
        throw new SqlParseException("Не удалось распознать команду: " + sql);
    }

    /*
     Разбор INSERT.
     1. Вытаскиваем список колонок, если он есть.
     2. Делим VALUES на группы "(...)" — каждая группа = одна строка.
     3. В каждой группе делим значения по запятым верхнего уровня.
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
     Разбор SELECT.
     Если после SELECT стоит "*" — оставляем selectColumns == null,
     исполнитель сам подставит все колонки по схеме.
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
     Разбор UPDATE.
     SET может содержать несколько присваиваний через запятую:
     "price = 30000, is_new = FALSE"
     Каждое кладём в LinkedHashMap, чтобы сохранить порядок.
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
     Разбор DELETE. WHERE не обязателен (но тогда удалим всё — осторожно).
     */
    private static Query parseDelete(Matcher m) {
        Query q = new Query();
        q.type = Query.Type.DELETE;
        q.tableName = m.group(1);
        q.whereClause = m.group(2);
        return q;
    }

    /*
     Разбор CREATE INDEX.
     Храним два поля: имя индекса (для сообщения) и колонку (для фактического построения).
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
     Разбор CREATE TABLE.
     Каждое объявление внутри скобок — либо "col TYPE", либо "PRIMARY KEY (col)".
     Атрибуты NOT NULL / DEFAULT / UNIQUE пока не поддерживаем — просто игнорируем.
     Если PRIMARY KEY не указан — берём первую колонку по умолчанию.
     */
    private static Query parseCreateTable(Matcher m) throws SqlParseException {
        Query q = new Query();
        q.type = Query.Type.CREATE_TABLE;
        q.tableName = m.group(1);
        q.createSchema = new LinkedHashMap<>();
        q.primaryKeyColumn = null;
        q.foreignKeys = new LinkedHashMap<>(); // <-- Инициализируем

        for (String part : SqlUtils.splitTopLevel(m.group(2), ',')) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) continue;

            if (trimmed.toUpperCase().startsWith("PRIMARY KEY")) {
                String inner = trimmed.substring("PRIMARY KEY".length()).trim();
                q.primaryKeyColumn = SqlUtils.stripParens(inner).trim();
            }
            // --- НОВОЕ: Обработка FOREIGN KEY ---
            else if (trimmed.toUpperCase().startsWith("FOREIGN KEY")) {
                // Ожидаем: FOREIGN KEY (car_id) REFERENCES cars(id)
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
     Разбор DROP TABLE.
     */
    private static Query parseDropTable(Matcher m) {
        Query q = new Query();
        q.type = Query.Type.DROP_TABLE;
        q.tableName = m.group(1);
        return q;
    }
}