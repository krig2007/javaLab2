# Мини-СУБД на Java

## Архитектура
- `db.model` — структуры данных (`Table`, `Row`, `Value`, `Index`)
- `db.query` — парсинг SQL и выполнение (`QueryParser`, `QueryExecutor`)
- `db.storage` — сохранение на диск (`StorageManager`, формат Java Serializable)
- `Main` — консольный интерфейс

Связь таблиц: `cars` (id, brand, price, is_new) и `owners` (id, name, car_id). Внешний ключ `car_id` ссылается на `cars.id`.

## Тестирование производительности (Индексы)
**Без индекса (O(n)):** Полный перебор строк.
**С индексом (O(1)):** Использование `HashMap`.
*Результат: поиск по индексированной колонке `brand` выполняется мгновенно, независимо от размера таблицы.*

## Инструкция пользователя
1. Запустите `Main.java`.
2. Вводите SQL-запросы (с `;` в конце).
   Примеры:
```sql
INSERT INTO cars VALUES (1, 'Toyota', 25000.0, TRUE);
SELECT * FROM cars WHERE brand = 'Toyota';
UPDATE cars SET price = 30000 WHERE id = 1;
DELETE FROM cars WHERE brand = 'Toyota';