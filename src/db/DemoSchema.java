package db;

import db.model.DataType;
import db.model.Database;
import db.model.Table;

import java.util.LinkedHashMap;

/**
Создаёт таблицы cars и owners при запуске программы, если их ещё нет.
В cars хранятся машины, первичный ключ — id, также есть индекс по brand.
В owners хранятся владельцы машин, car_id связан с cars.id.
 */
public final class DemoSchema {
    private DemoSchema() {}

    public static void setup(Database db) {
        try {
            if (!db.hasTable("cars")) {
                LinkedHashMap<String, DataType> schema = new LinkedHashMap<>();
                schema.put("id", DataType.INT);
                schema.put("brand", DataType.STRING);
                schema.put("price", DataType.DOUBLE);
                schema.put("is_new", DataType.BOOLEAN);
                Table cars = new Table("cars", schema, "id");
                cars.createIndex("brand");
                db.addTable(cars);
            }
            if (!db.hasTable("owners")) {
                LinkedHashMap<String, DataType> schema = new LinkedHashMap<>();
                schema.put("id", DataType.INT);
                schema.put("name", DataType.STRING);
                schema.put("car_id", DataType.INT);
                Table owners = new Table("owners", schema, "id");
                owners.addForeignKey("car_id", "cars.id");
                db.addTable(owners);
            }
        } catch (DbException e) {
            System.err.println("Не удалось создать демо-схему: " + e.getMessage());
        }
    }
}
