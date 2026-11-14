package db.query;

import db.model.DataType;

import java.util.LinkedHashMap;
import java.util.List;

/** Разобранный запрос. Заполняются поля, соответствующие {@link #type}. */
public class Query {
    public enum Type { INSERT, SELECT, UPDATE, DELETE, CREATE_INDEX, CREATE_TABLE, DROP_TABLE }

    public Type type;
    public String tableName;

    // INSERT
    public List<String> insertColumns;
    public List<List<String>> insertValues;

    // SELECT
    public List<String> selectColumns;

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