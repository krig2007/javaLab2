package db.storage;

import db.model.Database;

import java.io.*;
import java.nio.file.*;

/*
Загружает и сохраняет БД в файл database.db.
При сохранении сначала создаётся временный файл, а затем он заменяет основной.
Это позволяет не потерять данные, если во время записи произойдёт сбой.
Если файл повреждён, он переименовывается в .corrupt, а программа создаёт пустую БД.
*/
public final class StorageManager {
    private static final String FILE_NAME = "database.db";
    private static final String TEMP_NAME = FILE_NAME + ".tmp";

    private StorageManager() {}

    /*
     Атомарно Сохраняем БД на диск.
     Сначала записываем данные во временный файл, затем перемещаем его на место database.db.
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
     Загружаем БД из файла.
     Если файла нет, создаём новую пустую базу.
     Если файл повреждён, сохраняем его отдельно и запускаем пустую БД.
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
