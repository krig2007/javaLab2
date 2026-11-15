package db;

/**
 Базовое исключение мини-СУБД.
 Все доменные ошибки наследуются от него,
 чтобы их можно было ловить одним catch.
 */
public class DbException extends Exception {
    public DbException(String message) { super(message); }
    public DbException(String message, Throwable cause) { super(message, cause); }
}