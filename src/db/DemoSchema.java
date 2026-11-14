package db;

import db.model.DataType;
import db.model.Database;
import db.model.Table;

import java.util.LinkedHashMap;

/** Создание демонстрационных таблиц при первом запуске. */
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
                System.out.println("Создана таблица 'cars'.");
            }
            if (!db.hasTable("owners")) {
                LinkedHashMap<String, DataType> schema = new LinkedHashMap<>();
                schema.put("id", DataType.INT);
                schema.put("name", DataType.STRING);
                schema.put("car_id", DataType.INT);
                db.addTable(new Table("owners", schema, "id"));
                System.out.println("Создана таблица 'owners'.");
            }
        } catch (DbException e) {
            System.err.println("Не удалось создать демо-схему: " + e.getMessage());
        }
    }
}