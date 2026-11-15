package db.query;

import db.SqlParseException;
import db.model.DataType;

import java.util.ArrayList;
import java.util.List;

/**
 Вспомогательные функции для разбора SQL.
 */
public final class SqlUtils {
    private SqlUtils() {}

    /*
     Делим строку по разделителю, но игнорируем те, что внутри кавычек или скобок.
     Это нужно для VALUES (1, 'a, b'), (2, 'c') — запятая внутри кавычек не должна резать.
     */
    public static List<String> splitTopLevel(String s, char delim) {
        List<String> result = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuote = false;
        int depth = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\'') {
                if (inQuote && i + 1 < s.length() && s.charAt(i + 1) == '\'') {
                    cur.append("''");
                    i++;
                } else {
                    inQuote = !inQuote;
                    cur.append(c);
                }
            } else if (!inQuote && c == '(') {
                depth++; cur.append(c);
            } else if (!inQuote && c == ')') {
                depth--; cur.append(c);
            } else if (!inQuote && depth == 0 && c == delim) {
                if (cur.length() > 0) result.add(cur.toString().trim());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        if (cur.length() > 0) result.add(cur.toString().trim());
        return result;
    }

    /*
     Убираем внешние круглые скобки, если они есть.
     "(id, brand)" -> "id, brand"
     */
    public static String stripParens(String s) {
        s = s.trim();
        if (s.startsWith("(") && s.endsWith(")")) return s.substring(1, s.length() - 1).trim();
        return s;
    }

    /*
     Превращаем имя типа в DataType.
     Поддерживаем алиасы: INTEGER = INT, FLOAT = DOUBLE, VARCHAR = STRING и т.д.
     */
    public static DataType parseDataType(String s) throws SqlParseException {
        switch (s.toUpperCase()) {
            case "INT": case "INTEGER":                 return DataType.INT;
            case "DOUBLE": case "FLOAT": case "REAL":   return DataType.DOUBLE;
            case "STRING": case "VARCHAR": case "TEXT": return DataType.STRING;
            case "DATE":                                return DataType.DATE;
            case "BOOLEAN": case "BOOL":                return DataType.BOOLEAN;
            default: throw new SqlParseException("Неизвестный тип данных: " + s);
        }
    }
}