package db;

/**
 Попытка вставить/обновить запись с уже существующим первичным ключом.
 */
public class DuplicateKeyException extends DbException {
    public DuplicateKeyException(String message) { super(message); }
}