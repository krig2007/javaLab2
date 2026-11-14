package db.storage;

import db.model.Database;

import java.io.*;
import java.nio.file.*;

/**
 * Загрузка/сохранение БД.
 * <p>Сохранение атомарное: сначала пишем во временный файл, затем {@code ATOMIC_MOVE}.</p>
 * <p>Повреждённый файл при загрузке не теряется — переименовывается в {@code *.corrupt.<ts>}.</p>
 */
public final class StorageManager {
    private static final String FILE_NAME = "database.db";
    private static final String TEMP_NAME = FILE_NAME + ".tmp";

    private StorageManager() {}

    /** Атомарно сохраняет БД на диск. */
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

    /** Загружает БД с диска или создаёт новую, если файла нет. */
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