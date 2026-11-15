# СУБД на Java

### Над проектом работали студенты группы 25КНТ-1: 
- Куракин А. А.
- Крутов И. Д.

## Описание

Проект представляет собой небольшую реляционную систему управления базами данных, написанную на Java. Она поддерживает 
основные SQL-подобные команды, позволяет сохранять данные на диске и имеет графический интерфейс, созданный с помощью Swing.

## Архитектура

### Пакеты

Проект разделён на несколько пакетов, каждый из которых отвечает за свою часть работы:

- `db.model` — основные элементы базы данных: `Database`, `Table`, `Row`, `Value`, `Index`, `DataType`.
- `db.query` — разбор и выполнение запросов. Здесь находятся `QueryParser`, `QueryExecutor`, `WhereClause`, `SqlUtils`, `Query`, `QueryResult`.
- `db.storage` — отвечает за сохранение и загрузку базы данных (`StorageManager`).
- `db` — содержит исключения (`DbException`, `DuplicateKeyException`, `SqlParseException`, `TableNotFoundException`) и демо-схему (`DemoSchema`).
- `gui` — графический интерфейс приложения (`App`, `MainFrame`).
- `Main` — консольный интерфейс, который можно использовать для отладки.

### Основные классы

- **Table** — отвечает за хранение структуры таблицы, её строк, первичного ключа и индексов. Также проверяет уникальность первичного ключа и наличие связанных записей для внешних ключей.
- **Value** — хранит значение ячейки с учётом его типа: INT, DOUBLE, STRING, DATE или BOOLEAN.
- **Index** — использует хеш-индекс для поиска по определённой колонке.
- **StorageManager** — отвечает за сохранение базы данных в файл `database.db`. Чтобы избежать потери данных при записи, сначала создаётся временный файл, который затем заменяет основной через `ATOMIC_MOVE`. Если файл базы данных повреждён, создаётся его резервная копия и запускается пустая БД.

## Поддерживаемые SQL-команды

В программе реализованы следующие команды:

- `INSERT INTO table VALUES (...)` — добавление одной строки в таблицу.
- `INSERT INTO table (col1, col2) VALUES (...), (...)` — добавление нескольких строк с указанием нужных колонок.
- `SELECT * FROM table [WHERE condition]` — получение всех записей или только тех, которые подходят под условие.
- `SELECT col1, col2 FROM table [WHERE condition]` — получение данных из выбранных колонок.
- `UPDATE table SET col = value [WHERE condition]` — изменение существующих записей.
- `DELETE FROM table [WHERE condition]` — удаление записей, подходящих под условие.
- `CREATE TABLE table (col1 TYPE, col2 TYPE, PRIMARY KEY (col1), FOREIGN KEY (col) REFERENCES ref_table(ref_col))` — создание новой таблицы с указанием типов данных и ключей.
- `DROP TABLE table` — удаление таблицы.
- `CREATE INDEX idx_name ON table (column)` — создание индекса для выбранной колонки.

## Типы данных

Для хранения значений поддерживаются пять типов данных: `INT`, `DOUBLE`, `STRING`, `DATE`, `BOOLEAN`.

## Связи между таблицами

Для связи таблиц используются внешние ключи (`FOREIGN KEY`). Они указываются непосредственно при создании таблицы.

Например:

```sql
CREATE TABLE cars (
    id INT,
    brand STRING,
    price DOUBLE,
    is_new BOOLEAN,
    PRIMARY KEY (id)
);

CREATE TABLE owners (
    id INT,
    name STRING,
    car_id INT,
    PRIMARY KEY (id),
    FOREIGN KEY (car_id) REFERENCES cars(id)
);
```

В этом примере таблица `owners` связана с таблицей `cars` через колонку `car_id`. При добавлении новой записи в `owners` программа 
проверяет, существует ли соответствующий автомобиль в таблице `cars`, то есть есть ли такое значение в `cars.id`.

## Индексы и производительность

Индексы нужны для того, чтобы не перебирать всю таблицу при каждом поиске.

- Без индекса (O(n)) — программа последовательно проверяет каждую строку таблицы.
- С индексом (O(1)) — поиск выполняется через HashMap<значение, List<Row>>.

Например, при выполнении запроса `SELECT ... WHERE brand = 'Toyota'`, если для колонки `brand` создан индекс, программа использует его вместо полного перебора строк.

Это позволяет значительно ускорить поиск. При этом фактическое время выполнения зависит от количества совпадений и особенностей работы хеш-таблицы.

## Инструкция по запуску

1. Через IDE (тестировалось на IntelliJ IDEA): запустите `Main.java`.
2. Введите SQL-запрос в соответствующее поле или консоль и нажмите «Выполнить».
3. Все изменения автоматически сохраняются в `database.db`. При выходе из программы через GUI также появляется диалог сохранения.

## Примеры запросов

```sql
-- Создание таблиц
CREATE TABLE cars (id INT, brand STRING, price DOUBLE, is_new BOOLEAN, PRIMARY KEY (id));
CREATE TABLE owners (id INT, name STRING, car_id INT, PRIMARY KEY (id), FOREIGN KEY (car_id) REFERENCES cars(id));

-- Вставка
INSERT INTO cars VALUES (1, 'Toyota', 25000.0, TRUE);
INSERT INTO owners VALUES (1, 'Ivan', 1);

-- Выборка с фильтром
SELECT * FROM cars WHERE brand = 'Toyota';

-- Обновление
UPDATE cars SET price = 30000 WHERE id = 1;

-- Удаление
DELETE FROM cars WHERE brand = 'Toyota';

-- Создание и удаление таблицы
CREATE TABLE students (id INT, name STRING, grade DOUBLE, PRIMARY KEY (id));
DROP TABLE students;
```
