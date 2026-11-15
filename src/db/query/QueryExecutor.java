package db.query;

import db.*;
import db.model.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 Исполнитель запросов.
 Не печатает ничего сам — возвращает QueryResult, а GUI решает, как это показать.
 */
public class QueryExecutor {
    private final Database db;

    public QueryExecutor(Database db) { this.db = db; }

    // Точка входа: раздаём запрос по обработчикам
    public QueryResult execute(Query q) throws DbException {
        switch (q.type) {
            case INSERT:        return executeInsert(q);
            case SELECT:        return executeSelect(q);
            case UPDATE:        return executeUpdate(q);
            case DELETE:        return executeDelete(q);
            case CREATE_INDEX:  return executeCreateIndex(q);
            case CREATE_TABLE:  return executeCreateTable(q);
            case DROP_TABLE:    return executeDropTable(q);
            default: throw new DbException("Неизвестный тип запроса: " + q.type);
        }
    }

    // Найти таблицу или бросить понятную ошибку
    private Table requireTable(String name) throws TableNotFoundException {
        Table t = db.getTable(name);
        if (t == null) throw new TableNotFoundException(name);
        return t;
    }

    /*
     INSERT.
     Если insertColumns == null — берём все колонки по схеме.
     Иначе — только указанные, остальные останутся NULL.
     Проверяем, что количество значений совпадает с количеством колонок.
     */
    private QueryResult executeInsert(Query q) throws DbException {
        Table table = requireTable(q.tableName);
        List<String> cols = q.insertColumns != null
                ? q.insertColumns
                : new ArrayList<>(table.getSchema().keySet());
        for (String c : cols) {
            if (!table.getSchema().containsKey(c))
                throw new ExecutionException("Колонка не найдена: " + c);
        }
        int count = 0;
        for (List<String> rawRow : q.insertValues) {
            if (rawRow.size() != cols.size()) {
                throw new ExecutionException("Количество значений (" + rawRow.size()
                        + ") не совпадает с количеством колонок (" + cols.size() + ")");
            }
            Row row = new Row();
            for (int i = 0; i < cols.size(); i++) {
                String col = cols.get(i);
                DataType type = table.getSchema().get(col);
                Value v = WhereClause.parseLiteral(rawRow.get(i), type);
                row.set(col, v);
            }
            // --- ПРОВЕРКА ВНЕШНИХ КЛЮЧЕЙ ---
            for (java.util.Map.Entry<String, String> fk : table.getForeignKeys().entrySet()) {
                String fkCol = fk.getKey();
                String target = fk.getValue();
                String[] parts = target.split("\\.");
                if (parts.length != 2) continue;

                String refTableName = parts[0];
                String refColName = parts[1];

                Value fkValue = row.get(fkCol);
                if (fkValue != null && fkValue.getData() != null) {
                    Table refTable = db.getTable(refTableName);
                    if (refTable == null) {
                        throw new DbException("Ошибка внешнего ключа: таблица '" + refTableName + "' не найдена");
                    }

                    boolean found = false;
                    for (Row refRow : refTable.getRows()) {
                        Value refVal = refRow.get(refColName);
                        if (refVal != null && refVal.getData().equals(fkValue.getData())) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        throw new DbException("Ошибка внешнего ключа: " + fkCol + "=" + fkValue.getData() + " не существует в " + refTableName + "." + refColName);
                    }
                }
            }
            table.insert(row);
            count++;
        }
        return QueryResult.message("Успешно добавлено записей: " + count, true);
    }

    /*
     SELECT.
     Если в WHERE есть = по индексированной колонке — идём через индекс (O(1)).
     Иначе — полный скан (O(n)).
     После получения результата печатаем только запрошенные колонки.
     */
    private QueryResult executeSelect(Query q) throws DbException {
        Table table = requireTable(q.tableName);
        WhereClause wc = WhereClause.parse(q.whereClause);
        List<Row> result;
        String extraMsg = null;

        if (wc == null) {
            // Без WHERE — все строки
            result = new ArrayList<>(table.getRows());
        } else {
            DataType type = table.getSchema().get(wc.column);
            if (type == null) throw new ExecutionException("Колонка не найдена: " + wc.column);
            wc.prepare(type);

            if ("=".equals(wc.operator) && table.hasIndex(wc.column)) {
                // 1. Через индекс: сразу берём только строки с этим ключом
                Value key = WhereClause.parseLiteral(wc.rawValue, type);
                result = new ArrayList<>();
                if (key != null && key.getData() != null) {
                    for (Row r : table.getIndex(wc.column).lookup(key.getData())) {
                        if (wc.matches(r)) result.add(r);
                    }
                }
                extraMsg = "(использован индекс: " + wc.column + ")";
            } else {
                // 2. Полный скан
                result = new ArrayList<>();
                for (Row row : table.getRows()) if (wc.matches(row)) result.add(row);
            }
        }

        // Проверяем, что все запрошенные колонки есть в схеме
        List<String> cols = q.selectColumns != null
                ? q.selectColumns
                : new ArrayList<>(table.getSchema().keySet());
        for (String c : cols) {
            if (!table.getSchema().containsKey(c))
                throw new ExecutionException("Колонка не найдена: " + c);
        }
        return QueryResult.table(cols, result, extraMsg);
    }

    /*
     UPDATE.
     Сначала готовим все новые значения (чтобы не начать менять БД и упасть посередине),
     затем выбираем строки по WHERE и применяем изменения через Table.update.
     */
    private QueryResult executeUpdate(Query q) throws DbException {
        Table table = requireTable(q.tableName);
        WhereClause wc = WhereClause.parse(q.whereClause);
        if (wc != null) {
            DataType type = table.getSchema().get(wc.column);
            if (type == null) throw new ExecutionException("Колонка не найдена: " + wc.column);
            wc.prepare(type);
        }

        // Подготовка SET-значений
        LinkedHashMap<String, Value> prepared = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : q.setClauses.entrySet()) {
            String col = e.getKey();
            DataType type = table.getSchema().get(col);
            if (type == null) throw new ExecutionException("Колонка не найдена: " + col);
            prepared.put(col, WhereClause.parseLiteral(e.getValue(), type));
        }

        // Поиск строк под изменение
        List<Row> targets = new ArrayList<>();
        for (Row row : table.getRows()) {
            if (wc == null || wc.matches(row)) targets.add(row);
        }

        // Применение
        for (Row row : targets) {
            for (Map.Entry<String, Value> e : prepared.entrySet()) {
                table.update(row, e.getKey(), e.getValue());
            }
        }
        return QueryResult.message("Обновлено записей: " + targets.size(), true);
    }

    /*
     DELETE.
     Выбираем строки по WHERE, затем удаляем через Table.delete —
     он сам обновит PK и индексы.
     */
    private QueryResult executeDelete(Query q) throws DbException {
        Table table = requireTable(q.tableName);
        WhereClause wc = WhereClause.parse(q.whereClause);
        if (wc != null) {
            DataType type = table.getSchema().get(wc.column);
            if (type == null) throw new ExecutionException("Колонка не найдена: " + wc.column);
            wc.prepare(type);
        }
        List<Row> toDelete = new ArrayList<>();
        for (Row row : table.getRows()) {
            if (wc == null || wc.matches(row)) toDelete.add(row);
        }
        for (Row row : toDelete) table.delete(row);
        return QueryResult.message("Удалено записей: " + toDelete.size(), true);
    }

    // CREATE INDEX
    private QueryResult executeCreateIndex(Query q) throws DbException {
        Table table = requireTable(q.tableName);
        table.createIndex(q.indexColumn);
        return QueryResult.message("Создан индекс '" + q.indexName + "' на "
                + q.tableName + "(" + q.indexColumn + ")", true);
    }

    // CREATE TABLE
    private QueryResult executeCreateTable(Query q) throws DbException {
        if (db.hasTable(q.tableName))
            throw new ExecutionException("Таблица уже существует: " + q.tableName);
        Table t = new Table(q.tableName, q.createSchema, q.primaryKeyColumn);

        // Добавляем внешние ключи из запроса (если они были указаны в FOREIGN KEY)
        if (q.foreignKeys != null) {
            for (Map.Entry<String, String> fk : q.foreignKeys.entrySet()) {
                t.addForeignKey(fk.getKey(), fk.getValue());
            }
        }

        db.addTable(t);
        return QueryResult.message("Создана таблица '" + q.tableName + "'", true);
    }

    // DROP TABLE
    private QueryResult executeDropTable(Query q) throws DbException {
        if (!db.hasTable(q.tableName)) throw new TableNotFoundException(q.tableName);
        db.dropTable(q.tableName);
        return QueryResult.message("Удалена таблица '" + q.tableName + "'", true);
    }
}