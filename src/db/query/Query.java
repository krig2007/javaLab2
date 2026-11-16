package db.query;

import db.model.DataType;

import java.util.LinkedHashMap;
import java.util.List;

/**
 Хранит данные SQL-запроса после его разбора.
QueryParser заполняет нужные поля, а QueryExecutor использует их при выполнении запроса.
В зависимости от типа запроса часть полей может оставаться null.
 */
public class Query {
    public enum Type { INSERT, SELECT, UPDATE, DELETE, CREATE_INDEX, CREATE_TABLE, DROP_TABLE }

    public Type type;
    public String tableName;

    // INSERT
    public List<String> insertColumns;        // null - если указаны все колонки таблицы
    public List<List<String>> insertValues;    // значения для каждой добавляемой строки

    // SELECT
    public List<String> selectColumns;         // null или ["*"] — если нужно выбрать все колонки

    // UPDATE
    public LinkedHashMap<String, String> setClauses;

    // WHERE
    public String whereClause;

    // CREATE INDEX
    public String indexName;
    public String indexColumn;

    // CREATE TABLE
    public LinkedHashMap<String, DataType> createSchema;
    public String primaryKeyColumn;
    public LinkedHashMap<String, String> foreignKeys;
}
