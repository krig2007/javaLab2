package db.query;

import db.DbException;
import db.model.DataType;
import db.model.Row;
import db.model.Value;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * Условие WHERE: колонка, оператор, значение.
 * <p>Поддерживаются операторы: {@code =, !=, <>, <, >, <=, >=}.</p>
 */
public class WhereClause {
    private static final String[] OPERATORS = {">=", "<=", "<>", "!=", "=", ">", "<"};

    public final String column;
    public final String operator;
    public final String rawValue;

    private transient Value cachedTarget;
    private transient DataType cachedType;

    private WhereClause(String column, String operator, String rawValue) {
        this.column = column;
        this.operator = operator;
        this.rawValue = rawValue;
    }

    /** Разбирает строку условия. Возвращает {@code null} для пустой/отсутствующей строки. */
    public static WhereClause parse(String where) throws DbException {
        if (where == null) return null;
        where = where.trim();
        if (where.isEmpty()) return null;
        for (String op : OPERATORS) {
            int idx = where.indexOf(op);
            if (idx > 0) {
                String col = where.substring(0, idx).trim();
                String val = where.substring(idx + op.length()).trim();
                if (col.isEmpty() || val.isEmpty()) continue;
                return new WhereClause(col, op, val);
            }
        }
        throw new DbException("Не удалось разобрать условие WHERE: " + where);
    }

    /** Кэширует литерал значения, чтобы не парсить его на каждой строке. */
    public void prepare(DataType type) throws DbException {
        this.cachedType = type;
        this.cachedTarget = parseLiteral(rawValue, type);
    }

    /** Проверка строки с уже подготовленным типом (после {@link #prepare}). */
    public boolean matches(Row row) throws DbException {
        if (cachedType == null) throw new DbException("WhereClause не подготовлен");
        return matches(row, cachedType, cachedTarget);
    }

    /** Универсальная проверка строки. */
    public boolean matches(Row row, DataType type) throws DbException {
        return matches(row, type, parseLiteral(rawValue, type));
    }

    private boolean matches(Row row, DataType type, Value target) {
        Value rowValue = row.get(column);
        if (rowValue == null || rowValue.getData() == null) return false;
        if (target == null || target.getData() == null) return false;

        Object a = rowValue.getData();
        Object b = target.getData();

        switch (type) {
            case INT:
            case DOUBLE: {
                double da = ((Number) a).doubleValue();
                double db = ((Number) b).doubleValue();
                switch (operator) {
                    case "=":  return da == db;
                    case "!=":
                    case "<>": return da != db;
                    case ">":  return da > db;
                    case "<":  return da < db;
                    case ">=": return da >= db;
                    case "<=": return da <= db;
                }
                break;
            }
            case STRING: {
                int cmp = ((String) a).compareTo((String) b);
                switch (operator) {
                    case "=":  return cmp == 0;
                    case "!=":
                    case "<>": return cmp != 0;
                    case ">":  return cmp > 0;
                    case "<":  return cmp < 0;
                    case ">=": return cmp >= 0;
                    case "<=": return cmp <= 0;
                }
                break;
            }
            case DATE: {
                int cmp = ((LocalDate) a).compareTo((LocalDate) b);
                switch (operator) {
                    case "=":  return cmp == 0;
                    case "!=":
                    case "<>": return cmp != 0;
                    case ">":  return cmp > 0;
                    case "<":  return cmp < 0;
                    case ">=": return cmp >= 0;
                    case "<=": return cmp <= 0;
                }
                break;
            }
            case BOOLEAN: {
                boolean ba = (Boolean) a;
                boolean bb = (Boolean) b;
                switch (operator) {
                    case "=":  return ba == bb;
                    case "!=":
                    case "<>": return ba != bb;
                    default:   return false;
                }
            }
        }
        return false;
    }

    /** Преобразует литерал SQL в {@link Value} указанного типа. */
    public static Value parseLiteral(String raw, DataType type) throws DbException {
        raw = raw.trim();
        if (raw.equalsIgnoreCase("NULL")) return null;
        try {
            switch (type) {
                case INT:
                    return new Value(type, Integer.parseInt(raw));
                case DOUBLE:
                    return new Value(type, Double.parseDouble(raw));
                case STRING:
                    if (!raw.startsWith("'"))
                        throw new DbException("Строковое значение должно быть в кавычках: " + raw);
                    return new Value(type, unquote(raw));
                case BOOLEAN:
                    if (!raw.equalsIgnoreCase("TRUE") && !raw.equalsIgnoreCase("FALSE"))
                        throw new DbException("Ожидалось TRUE/FALSE, получено: " + raw);
                    return new Value(type, Boolean.parseBoolean(raw));
                case DATE:
                    return new Value(type, LocalDate.parse(unquote(raw)));
                default:
                    throw new DbException("Неизвестный тип: " + type);
            }
        } catch (NumberFormatException | DateTimeParseException e) {
            throw new DbException("Не удалось разобрать значение '" + raw + "' как " + type, e);
        }
    }

    private static String unquote(String s) {
        s = s.trim();
        if (s.length() >= 2 && s.charAt(0) == '\'' && s.charAt(s.length() - 1) == '\'') {
            return s.substring(1, s.length() - 1).replace("''", "'");
        }
        return s;
    }
}