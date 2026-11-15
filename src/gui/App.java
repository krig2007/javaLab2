package gui;

import db.DemoSchema;
import db.model.Database;
import db.storage.StorageManager;

import javax.swing.*;

/**
 Точка входа GUI.
 Загружаем БД, создаём демо-таблицы (если их нет) и открываем окно.
 Создание окна ставим в EDT через invokeLater — так требует Swing.
 */
public final class App {
    private App() {}

    public static void launch() {
        // 1. Ставим системный Look and Feel
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) { /* fallback look&feel */ }

        // 2. Загружаем БД и создаём демо-схему
        Database db = StorageManager.load();
        // DemoSchema.setup(db);

        // 3. Открываем окно в потоке EDT
        SwingUtilities.invokeLater(() -> new MainFrame(db).setVisible(true));
    }

    public static void main(String[] args) {
        launch();
    }
}