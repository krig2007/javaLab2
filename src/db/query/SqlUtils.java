package db.query;

import db.SqlParseException;
import db.model.DataType;

import java.util.ArrayList;
import java.util.List;

/** Утилиты для разбора SQL. */
public final class SqlUtils {
    private SqlUtils() {}

    /**
     * Делит строку по разделителю, игнорируя вхождения внутри кавычек и на вложенных уровнях скобок.
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

    /** Убирает внешние круглые скобки. */
    public static String stripParens(String s) {
        s = s.trim();
        if (s.startsWith("(") && s.endsWith(")")) return s.substring(1, s.length() - 1).trim();
        return s;
    }

    /** Разбирает имя типа данных. */
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