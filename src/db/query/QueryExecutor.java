package db.query;

import db.*;
import db.model.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
Выполняет SQL-запросы и возвращает результат их выполнения.
Сам ничего не выводит - отображением результата занимается GUI.
 */
public class QueryExecutor {
    private final Database db;

    public QueryExecutor(Database db) { this.db = db; }

    // Определяем тип запроса и вызываем нужный метод
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

    // Получаем таблицу по имени. Если её нет, выбрасываем исключение
    private Table requireTable(String name) throws TableNotFoundException {
        Table t = db.getTable(name);
        if (t == null) throw new TableNotFoundException(name);
        return t;
    }

    /*
    Добавляем новые записи в таблицу.
    Если insertColumns == null, используются все колонки таблицы.
    Если колонки указаны вручную, остальные получат значение NULL.
    Перед вставкой проверяем, что количество значений совпадает с количеством колонок.
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
            // Проверяем, существуют ли записи, на которые ссылаются внешние ключи
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
    Получаем записи из таблицы.
    Если условие WHERE использует индексированную колонку и оператор =,
    поиск выполняется через индекс. В остальных случаях проверяются все строки.
    В результате остаются только запрошенные колонки.
     */
    private QueryResult executeSelect(Query q) throws DbException {
        Table table = requireTable(q.tableName);
        WhereClause wc = WhereClause.parse(q.whereClause);
        List<Row> result;
        String extraMsg = null;

        if (wc == null) {
            // Если WHERE нет, возвращаем все строки таблицы
            result = new ArrayList<>(table.getRows());
        } else {
            DataType type = table.getSchema().get(wc.column);
            if (type == null) throw new ExecutionException("Колонка не найдена: " + wc.column);
            wc.prepare(type);

            if ("=".equals(wc.operator) && table.hasIndex(wc.column)) {
                // Ищем нужные строки через индекс
                Value key = WhereClause.parseLiteral(wc.rawValue, type);
                result = new ArrayList<>();
                if (key != null && key.getData() != null) {
                    for (Row r : table.getIndex(wc.column).lookup(key.getData())) {
                        if (wc.matches(r)) result.add(r);
                    }
                }
                extraMsg = "(использован индекс: " + wc.column + ")";
            } else {
                // Если индекс не подходит, проверяем строки по очереди
                result = new ArrayList<>();
                for (Row row : table.getRows()) if (wc.matches(row)) result.add(row);
            }
        }

        // Проверяем, что все указанные колонки есть в таблице
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
    Обновляем записи в таблице.
    Сначала проверяем и подготавливаем новые значения, чтобы не менять данные,
    если в процессе обнаружится ошибка. Затем находим нужные строки и обновляем их
     */
    private QueryResult executeUpdate(Query q) throws DbException {
        Table table = requireTable(q.tableName);
        WhereClause wc = WhereClause.parse(q.whereClause);
        if (wc != null) {
            DataType type = table.getSchema().get(wc.column);
            if (type == null) throw new ExecutionException("Колонка не найдена: " + wc.column);
            wc.prepare(type);
        }

        // Проверяем значения, которые нужно записать в колонки
        LinkedHashMap<String, Value> prepared = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : q.setClauses.entrySet()) {
            String col = e.getKey();
            DataType type = table.getSchema().get(col);
            if (type == null) throw new ExecutionException("Колонка не найдена: " + col);
            prepared.put(col, WhereClause.parseLiteral(e.getValue(), type));
        }

        // Находим строки, которые подходят под условие WHERE
        List<Row> targets = new ArrayList<>();
        for (Row row : table.getRows()) {
            if (wc == null || wc.matches(row)) targets.add(row);
        }

        // Записываем новые значения
        for (Row row : targets) {
            for (Map.Entry<String, Value> e : prepared.entrySet()) {
                table.update(row, e.getKey(), e.getValue());
            }
        }
        return QueryResult.message("Обновлено записей: " + targets.size(), true);
    }

    /*
    Удаляем записи, подходящие под условие WHERE.
    После поиска передаём их в Table.delete, который также обновляет первичный ключ и индексы.
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

    // Создаём индекс для указанной колонки
    private QueryResult executeCreateIndex(Query q) throws DbException {
        Table table = requireTable(q.tableName);
        table.createIndex(q.indexColumn);
        return QueryResult.message("Создан индекс '" + q.indexName + "' на "
                + q.tableName + "(" + q.indexColumn + ")", true);
    }

    // Создаём новую таблицу
    private QueryResult executeCreateTable(Query q) throws DbException {
        if (db.hasTable(q.tableName))
            throw new ExecutionException("Таблица уже существует: " + q.tableName);
        Table t = new Table(q.tableName, q.createSchema, q.primaryKeyColumn);

        // Добавляем внешние ключи, если они указаны в запросе
        if (q.foreignKeys != null) {
            for (Map.Entry<String, String> fk : q.foreignKeys.entrySet()) {
                t.addForeignKey(fk.getKey(), fk.getValue());
            }
        }

        db.addTable(t);
        return QueryResult.message("Создана таблица '" + q.tableName + "'", true);
    }

    // Удаляем таблицу из базы данных
    private QueryResult executeDropTable(Query q) throws DbException {
        if (!db.hasTable(q.tableName)) throw new TableNotFoundException(q.tableName);
        db.dropTable(q.tableName);
        return QueryResult.message("Удалена таблица '" + q.tableName + "'", true);
    }
}
