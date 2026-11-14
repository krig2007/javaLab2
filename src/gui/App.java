package gui;

import db.model.Database;
import db.storage.StorageManager;
import db.DemoSchema;

import javax.swing.*;

/** Точка входа GUI */
public final class App {
    private App() {}

    /** Запускает Swing-приложение. */
    public static void launch() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) { /* fallback look&feel */ }

        SwingUtilities.invokeLater(() -> {
            Database db = StorageManager.load();
            DemoSchema.setup(db);
            new MainFrame(db).setVisible(true);
        });
    }

    public static void main(String[] args) {
        launch();
    }
}