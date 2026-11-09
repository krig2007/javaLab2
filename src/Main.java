import db.model.*;
import db.query.QueryExecutor;
import db.storage.StorageManager;

import java.util.LinkedHashMap;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        // 1. Загружаем базу данных с диска (или создаем новую, если файла нет)
        Database db = StorageManager.load();
        Scanner scanner = new Scanner(System.in);

        // 2. Если база пустая (запустили впервые), создаем тестовую таблицу cars
        if (!db.hasTable("cars")) {
            LinkedHashMap<String, DataType> schema = new LinkedHashMap<>();
            schema.put("id", DataType.INT);
            schema.put("brand", DataType.STRING);
            schema.put("price", DataType.DOUBLE);
            schema.put("is_new", DataType.BOOLEAN);

            Table cars = new Table("cars", schema, "id");
            db.addTable(cars);
            cars.createIndex("brand");
            System.out.println("Создана новая таблица 'cars'.");

        }

        if (!db.hasTable("owners")) {
            LinkedHashMap<String, DataType> ownersSchema = new LinkedHashMap<>();
            ownersSchema.put("id", DataType.INT);
            ownersSchema.put("name", DataType.STRING);
            ownersSchema.put("car_id", DataType.INT);

            Table owners = new Table("owners", ownersSchema, "id");
            db.addTable(owners);
            System.out.println("Создана новая таблица 'owners'.");
        }

        System.out.println("=== Добро пожаловать в мини-СУБД ===");
        System.out.println("Введите SQL-запрос (или 'exit' для выхода):");

        // 3. Бесконечный цикл ввода команд
        while (true) {
            System.out.print("SQL> ");
            String input = scanner.nextLine().trim();

            if (input.equalsIgnoreCase("exit")) {
                System.out.println("Сохранение базы данных...");
                StorageManager.save(db); // Сохраняем перед выходом
                break;
            }

            if (input.isEmpty()) continue;

            // 4. Передаем запрос исполнителю
            QueryExecutor.execute(input, db);
        }

        scanner.close();
    }
}