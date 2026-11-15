package db;

/**
 Ошибка выполнения запроса:
 колонка не найдена, тип не совпадает, количество значений не то.
 Бросается исполнителем.
 */
public class ExecutionException extends DbException {
    public ExecutionException(String message) { super(message); }
    public ExecutionException(String message, Throwable cause) { super(message, cause); }
}