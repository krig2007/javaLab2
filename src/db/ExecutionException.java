package db;

/** Ошибка выполнения запроса (несоответствие типов, неизвестная колонка и т.п.). */
public class ExecutionException extends DbException {
    public ExecutionException(String message) { super(message); }
    public ExecutionException(String message, Throwable cause) { super(message, cause); }
}