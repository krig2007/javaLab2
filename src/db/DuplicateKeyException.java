package db;

/** Нарушение уникальности первичного ключа. */
public class DuplicateKeyException extends DbException {
    public DuplicateKeyException(String message) { super(message); }
}