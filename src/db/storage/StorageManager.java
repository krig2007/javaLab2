package db.storage;

import db.model.Database;

import java.io.*;
import java.nio.file.*;

/**
 Загрузка и сохранение БД в один файл database.db.
 Сохранение атомарное: сначала пишем в .tmp, потом переименовываем.
 Так при сбое посреди записи не останется «полу-файла» на месте database.db.
 Если файл окажется битым — переименовываем его в .corrupt, чтобы не потерять,
 и стартуем с пустой БД.
 */
public final class StorageManager {
    private static final String FILE_NAME = "database.db";
    private static final String TEMP_NAME = FILE_NAME + ".tmp";

    private StorageManager() {}

    /*
     Атомарно сохраняем БД на диск.
     1. Пишем во временный файл.
     2. Перемещаем его поверх database.db с флагом ATOMIC_MOVE.
     */
    public static void save(Database db) throws IOException {
        File tmp = new File(TEMP_NAME);
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(tmp))) {
            oos.writeObject(db);
            oos.flush();
        }
        try {
            Files.move(tmp.toPath(), new File(FILE_NAME).toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp.toPath(), new File(FILE_NAME).toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /*
     Загружаем БД.
     Если файла нет — возвращаем пустую БД.
     Если файл битый (например, старая версия формата) — переименовываем и стартуем с пустой.
     */
    public static Database load() {
        File file = new File(FILE_NAME);
        if (!file.exists()) return new Database();
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
            return (Database) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Ошибка загрузки БД: " + e.getMessage());
            File backup = new File(FILE_NAME + ".corrupt." + System.currentTimeMillis());
            if (file.renameTo(backup)) {
                System.err.println("Повреждённый файл сохранён как " + backup.getName());
            }
            System.err.println("Создана пустая база.");
            return new Database();
        }
    }
}