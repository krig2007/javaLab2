package db.query;

import db.model.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class QueryExecutor {

    public static void execute(String sql, Database db) {
        QueryParser.Query q = QueryParser.parse(sql);
        if (q == null) {
            System.out.println("Ошибка: Не удалось распознать команду. Проверьте синтаксис.");
            return;
        }

        Table table = db.getTable(q.tableName);
        if (table == null && !q.type.equals("CREATE")) { // CREATE мы пока не делаем
            System.out.println("Ошибка: Таблица '" + q.tableName + "' не существует.");
            return;
        }

        try {
            switch (q.type) {
                case "INSERT":
                    executeInsert(q, table);
                    break;
                case "SELECT":
                    executeSelect(q, table);
                    break;
                case "UPDATE":
                    executeUpdate(q, table);
                    break;
                case "DELETE":
                    executeDelete(q, table);
                    break;
                default:
                    System.out.println("Ошибка: Неизвестная команда.");
            }
        } catch (Exception e) {
            System.out.println("Ошибка выполнения: " + e.getMessage());
        }
    }

    // --- Логика INSERT ---
    private static void executeInsert(QueryParser.Query q, Table table) throws Exception {
        // Разбиваем строку значений по запятым, игнорируя запятые внутри кавычек (упрощенно)
        String[] rawValues = q.columnsOrValues.split(",");
        Row row = new Row();

        int index = 0;
        for (Map.Entry<String, DataType> col : table.getSchema().entrySet()) {
            if (index >= rawValues.length) break;

            String raw = rawValues[index].trim();
            Value val = parseValue(raw, col.getValue());
            row.set(col.getKey(), val);
            index++;
        }

        table.insert(row);
        System.out.println("Успешно добавлена 1 запись.");
    }

    // --- Логика SELECT ---
    private static void executeSelect(QueryParser.Query q, Table table) {
        List<Row> rows = table.getRows();
        List<Row> filteredRows = new ArrayList<>();
        boolean indexUsed = false;

        // 1. Пробуем использовать индекс
        if (q.whereClause != null && q.whereClause.contains("=")) {
            String col = q.whereClause.split("=")[0].trim();
            Index idx = table.getIndex(col);
            if (idx != null) {
                String valStr = q.whereClause.split("=")[1].trim().replace("'", "");
                filteredRows = idx.getRows(valStr);
                indexUsed = true;
                System.out.println("(Использован индекс для ускорения)");
            }
        }

        // 2. Если индекс не использовался — делаем полный перебор (O(n))
        if (!indexUsed) {
            if (q.whereClause != null && !q.whereClause.trim().isEmpty()) {
                for (Row row : rows) {
                    if (matchesWhere(row, q.whereClause)) {
                        filteredRows.add(row);
                    }
                }
            } else {
                filteredRows.addAll(rows);
            }
        }

        // 3. Вывод результата (выравнивание)
        Map<String, Integer> colWidths = new LinkedHashMap<>();
        for (String col : table.getSchema().keySet()) {
            colWidths.put(col, col.length());
        }

        for (Row row : filteredRows) {
            for (String col : table.getSchema().keySet()) {
                int len = row.get(col).toString().length();
                if (len > colWidths.get(col)) {
                    colWidths.put(col, len);
                }
            }
        }

        System.out.println("Результат запроса:");

        StringBuilder header = new StringBuilder();
        for (String col : table.getSchema().keySet()) {
            header.append(String.format("%-" + (colWidths.get(col) + 2) + "s", col));
        }
        System.out.println(header.toString());

        for (Row row : filteredRows) {
            StringBuilder line = new StringBuilder();
            for (String col : table.getSchema().keySet()) {
                line.append(String.format("%-" + (colWidths.get(col) + 2) + "s", row.get(col).toString()));
            }
            System.out.println(line.toString());
        }
    }

    // Вспомогательный метод для проверки условия WHERE
    private static boolean matchesWhere(Row row, String whereClause) {
        // Пока поддерживаем только простые условия: колонка оператор значение
        // Например: price > 20000, brand = 'Toyota', id = 1
        whereClause = whereClause.trim();

        // Ищем операторы >=, <=, !=, =, >, <
        String[] operators = {">=", "<=", "!=", "=", ">", "<"};
        String op = null;
        int opIndex = -1;

        for (String operator : operators) {
            opIndex = whereClause.indexOf(operator);
            if (opIndex != -1) {
                op = operator;
                break;
            }
        }

        if (op == null) {
            throw new RuntimeException("Неверный оператор в WHERE: " + whereClause);
        }

        String column = whereClause.substring(0, opIndex).trim();
        String rawValue = whereClause.substring(opIndex + op.length()).trim();

        Value rowValue = row.get(column);
        if (rowValue == null) {
            throw new RuntimeException("Колонка не найдена в таблице: " + column);
        }

        // Парсим значение из строки в правильный тип (как в INSERT)
        Value targetValue = parseValue(rawValue, rowValue.getType());

        // Сравниваем значения (все приводим к одному типу)
        Object val1 = rowValue.getData();
        Object val2 = targetValue.getData();

        if (val1 instanceof Number && val2 instanceof Number) {
            double d1 = ((Number) val1).doubleValue();
            double d2 = ((Number) val2).doubleValue();
            switch (op) {
                case "=": return d1 == d2;
                case "!=": return d1 != d2;
                case ">": return d1 > d2;
                case "<": return d1 < d2;
                case ">=": return d1 >= d2;
                case "<=": return d1 <= d2;
            }
        } else if (val1 instanceof String && val2 instanceof String) {
            String s1 = (String) val1;
            String s2 = (String) val2;
            switch (op) {
                case "=": return s1.equals(s2);
                case "!=": return !s1.equals(s2);
                case ">": return s1.compareTo(s2) > 0;
                case "<": return s1.compareTo(s2) < 0;
                case ">=": return s1.compareTo(s2) >= 0;
                case "<=": return s1.compareTo(s2) <= 0;
            }
        } else if (val1 instanceof Boolean && val2 instanceof Boolean) {
            boolean b1 = (Boolean) val1;
            boolean b2 = (Boolean) val2;
            switch (op) {
                case "=": return b1 == b2;
                case "!=": return b1 != b2;
            }
        }
        return false;
    }

    // Вспомогательный метод: превращает строку из SQL в Value
    private static Value parseValue(String raw, DataType type) {
        raw = raw.replace("'", ""); // Убираем кавычки для строк

        switch (type) {
            case INT:
                return new Value(type, Integer.parseInt(raw));
            case DOUBLE:
                return new Value(type, Double.parseDouble(raw));
            case STRING:
                return new Value(type, raw);
            case BOOLEAN:
                return new Value(type, Boolean.parseBoolean(raw));
            case DATE:
                // Пока упрощенно, для простоты можно сохранять как строку
                return new Value(type, raw);
            default:
                return null;
        }
    }
    // --- Логика UPDATE ---
    private static void executeUpdate(QueryParser.Query q, Table table) throws Exception {
        // q.columnsOrValues содержит "price = 30000" (то, что после SET)
        // q.whereClause содержит "id = 2"

        String[] parts = q.columnsOrValues.split("=");
        if (parts.length != 2) {
            throw new Exception("Неверный синтаксис UPDATE. Ожидается: SET колонка = значение");
        }
        String colName = parts[0].trim();
        String rawValue = parts[1].trim();

        DataType type = table.getSchema().get(colName);
        if (type == null) throw new Exception("Колонка не найдена: " + colName);

        Value newValue = parseValue(rawValue, type);

        int count = 0;
        // Проходим по всем строкам, проверяем WHERE
        for (Row row : table.getRows()) {
            if (q.whereClause == null || matchesWhere(row, q.whereClause)) {
                row.set(colName, newValue);
                count++;
            }
        }
        System.out.println("Обновлено записей: " + count);
    }

    // --- Логика DELETE ---
    private static void executeDelete(QueryParser.Query q, Table table) {
        List<Row> rowsToKeep = new ArrayList<>();
        int count = 0;

        for (Row row : table.getRows()) {
            if (q.whereClause == null || matchesWhere(row, q.whereClause)) {
                count++; // Эту строку удаляем
            } else {
                rowsToKeep.add(row); // Эту оставляем
            }
        }

        // Очищаем список и кладем обратно только те, что выжили
        table.getRows().clear();
        table.getRows().addAll(rowsToKeep);

        System.out.println("Удалено записей: " + count);
    }
}