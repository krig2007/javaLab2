package db;

import db.model.DataType;
import db.model.Database;
import db.model.Table;

import java.util.LinkedHashMap;

/**
 Создаёт демонстрационные таблицы при первом запуске.
 cars  — машины, PK = id, индекс по brand.
 owners — владельцы, PK = id, car_id ссылается на cars.id.
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