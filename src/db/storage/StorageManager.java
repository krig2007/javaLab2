package db.storage;

import db.model.Database;

import java.io.*;

public class StorageManager {
    private static final String FILE_NAME = "database.db";

    // Сохранение базы данных в файл
    public static void save(Database db) {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(FILE_NAME))) {
            oos.writeObject(db);
            System.out.println("База данных успешно сохранена в файл " + FILE_NAME);
        } catch (IOException e) {
            System.err.println("Ошибка при сохранении базы данных: " + e.getMessage());
        }
    }

    // Загрузка базы данных из файла
    public static Database load() {
        File file = new File(FILE_NAME);
        if (!file.exists()) {
            System.out.println("Файл базы данных не найден. Создана новая пустая база.");
            return new Database();
        }

        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(FILE_NAME))) {
            return (Database) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Ошибка при загрузке базы данных: " + e.getMessage());
            return new Database(); // Если файл битый, возвращаем пустую базу
        }
    }
}