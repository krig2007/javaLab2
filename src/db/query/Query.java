package db.query;

import db.model.DataType;

import java.util.LinkedHashMap;
import java.util.List;

/**
 Разобранный SQL-запрос.
 Сюда QueryParser складывает все части, а QueryExecutor их читает.
 Поля заполняются в зависимости от type — остальные остаются null.
 */
public class Query {
    public enum Type { INSERT, SELECT, UPDATE, DELETE, CREATE_INDEX, CREATE_TABLE, DROP_TABLE }

    public Type type;
    public String tableName;

    // INSERT
    public List<String> insertColumns;        // null — значит все колонки по схеме
    public List<List<String>> insertValues;    // список строк, каждая = список литералов

    // SELECT
    public List<String> selectColumns;         // null или ["*"] — значит все

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
}