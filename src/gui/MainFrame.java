package gui;

import db.DbException;
import db.model.Database;
import db.model.Row;
import db.model.Value;
import db.query.Query;
import db.query.QueryExecutor;
import db.query.QueryParser;
import db.query.QueryResult;
import db.storage.StorageManager;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Главное окно мини-СУБД.
 * <p>Содержит:</p>
 * <ul>
 *   <li>слева — список таблиц (двойной клик открывает {@code SELECT *});</li>
 *   <li>сверху — поле ввода SQL + кнопки;</li>
 *   <li>снизу — вкладки «Результат» (JTable) и «Лог».</li>
 * </ul>
 */
public class MainFrame extends JFrame {

    private final Database db;
    private final QueryExecutor executor;

    private final JTextArea sqlArea = new JTextArea(4, 60);
    private final DefaultTableModel resultModel;
    private final JTable resultTable;
    private final JTextArea logArea = new JTextArea();
    private final JTabbedPane resultTabs = new JTabbedPane();
    private final JLabel statusLabel = new JLabel("Готово");
    private final DefaultListModel<String> tableListModel = new DefaultListModel<>();
    private final JList<String> tableList = new JList<>(tableListModel);
    private JButton executeButton;

    public MainFrame(Database db) {
        super("Мини-СУБД — Swing");
        this.db = db;
        this.executor = new QueryExecutor(db);

        this.resultModel = new DefaultTableModel() {
            @Override public boolean isCellEditable(int row, int col) { return false; }
        };
        this.resultTable = new JTable(resultModel);
        this.resultTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        this.resultTable.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        this.sqlArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        this.sqlArea.setLineWrap(true);
        this.sqlArea.setWrapStyleWord(true);

        this.logArea.setEditable(false);
        this.logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        buildLayout();
        buildMenu();
        installShortcuts();
        installTableListBehavior();
        refreshTableList();

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { onExit(); }
        });
        setSize(1100, 750);
        setLocationRelativeTo(null);
    }

    // ---------- построение интерфейса ----------

    private void buildLayout() {
        JPanel tablesPanel = new JPanel(new BorderLayout(0, 4));
        tablesPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 4, 4));
        tablesPanel.add(new JLabel("Таблицы (двойной клик — открыть):"), BorderLayout.NORTH);
        tableList.setVisibleRowCount(6);
        tablesPanel.add(new JScrollPane(tableList), BorderLayout.CENTER);
        tablesPanel.setPreferredSize(new Dimension(220, 140));

        JPanel sqlPanel = new JPanel(new BorderLayout(4, 4));
        sqlPanel.setBorder(BorderFactory.createEmptyBorder(8, 4, 4, 8));
        sqlPanel.add(new JLabel("SQL-запрос (Ctrl+Enter — выполнить):"), BorderLayout.NORTH);
        sqlPanel.add(new JScrollPane(sqlArea), BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        JButton clearBtn = new JButton("Очистить");
        clearBtn.addActionListener(e -> sqlArea.setText(""));
        JButton saveBtn = new JButton("Сохранить БД");
        saveBtn.addActionListener(e -> saveDb());
        executeButton = new JButton("Выполнить");
        executeButton.addActionListener(this::onExecute);
        btnPanel.add(clearBtn);
        btnPanel.add(saveBtn);
        btnPanel.add(executeButton);
        sqlPanel.add(btnPanel, BorderLayout.SOUTH);

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(tablesPanel, BorderLayout.WEST);
        topPanel.add(sqlPanel, BorderLayout.CENTER);

        resultTabs.addTab("Результат", new JScrollPane(resultTable));
        resultTabs.addTab("Лог", new JScrollPane(logArea));

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, topPanel, resultTabs);
        split.setResizeWeight(0.28);
        add(split, BorderLayout.CENTER);

        statusLabel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        add(statusLabel, BorderLayout.SOUTH);

        sqlArea.setText("SELECT * FROM cars;");
        sqlArea.selectAll();
    }

    private void buildMenu() {
        JMenuBar bar = new JMenuBar();

        JMenu fileMenu = new JMenu("Файл");
        JMenuItem saveItem = new JMenuItem("Сохранить БД");
        saveItem.addActionListener(e -> saveDb());
        JMenuItem exitItem = new JMenuItem("Выход");
        exitItem.addActionListener(e -> onExit());
        fileMenu.add(saveItem);
        fileMenu.addSeparator();
        fileMenu.add(exitItem);

        bar.add(fileMenu);
        setJMenuBar(bar);
    }

    private void installShortcuts() {
        sqlArea.getInputMap().put(KeyStroke.getKeyStroke("ctrl ENTER"), "execute");
        sqlArea.getActionMap().put("execute", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { onExecute(e); }
        });
    }

    private void installTableListBehavior() {
        tableList.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    String t = tableList.getSelectedValue();
                    if (t != null) {
                        sqlArea.setText("SELECT * FROM " + t + ";");
                        onExecute(null);
                    }
                }
            }
        });
    }

    // ---------- выполнение запроса ----------

    private void onExecute(ActionEvent e) {
        String sql = sqlArea.getText().trim();
        if (sql.isEmpty()) return;
        if (sql.endsWith(";")) sql = sql.substring(0, sql.length() - 1);

        final String[] statements = sql.split(";\\s*");
        setBusy(true);
        statusLabel.setText("Выполняется...");

        new SwingWorker<List<QueryResult>, Void>() {
            @Override protected List<QueryResult> doInBackground() {
                List<QueryResult> results = new ArrayList<>();
                for (String part : statements) {
                    if (part.trim().isEmpty()) continue;
                    try {
                        Query q = QueryParser.parse(part);
                        results.add(executor.execute(q));
                    } catch (DbException ex) {
                        results.add(QueryResult.error(ex.getMessage()));
                        break;
                    }
                }
                return results;
            }

            @Override protected void done() {
                boolean modified = false;
                try {
                    for (QueryResult r : get()) modified |= applyResult(r);
                } catch (Exception ex) {
                    log("Внутренняя ошибка: " + ex.getMessage());
                }
                if (modified) saveDb();
                refreshTableList();
                setBusy(false);
                statusLabel.setText("Готово");
            }
        }.execute();
    }

    private boolean applyResult(QueryResult r) {
        switch (r.kind) {
            case MESSAGE:
                log(r.message);
                break;
            case TABLE:
                showTable(r.columns, r.rows);
                StringBuilder msg = new StringBuilder();
                if (r.message != null) msg.append(r.message).append(' ');
                msg.append("Получено строк: ").append(r.rows.size());
                log(msg.toString().trim());
                break;
            case ERROR:
                log("ОШИБКА: " + r.message);
                break;
        }
        return r.modified;
    }

    private void showTable(List<String> columns, List<Row> rows) {
        resultModel.setRowCount(0);
        resultModel.setColumnCount(0);
        for (String c : columns) resultModel.addColumn(c);
        for (Row row : rows) {
            Object[] arr = new Object[columns.size()];
            for (int i = 0; i < columns.size(); i++) {
                Value v = row.get(columns.get(i));
                arr[i] = (v == null || v.getData() == null) ? null : v.getData();
            }
            resultModel.addRow(arr);
        }
        resultTabs.setSelectedIndex(0);
        autoSizeColumns();
    }

    private void autoSizeColumns() {
        FontMetrics fm = resultTable.getFontMetrics(resultTable.getFont());
        for (int i = 0; i < resultTable.getColumnCount(); i++) {
            int width = fm.stringWidth(resultTable.getColumnName(i)) + 30;
            int limit = Math.min(resultTable.getRowCount(), 200);
            for (int j = 0; j < limit; j++) {
                Object val = resultTable.getValueAt(j, i);
                if (val != null) {
                    int w = fm.stringWidth(val.toString()) + 20;
                    if (w > width) width = w;
                }
            }
            resultTable.getColumnModel().getColumn(i).setPreferredWidth(Math.min(width, 400));
        }
    }

    private void setBusy(boolean busy) {
        executeButton.setEnabled(!busy);
        statusLabel.setText(busy ? "Выполняется..." : "Готово");
    }

    // ---------- вспомогательное ----------

    private void log(String msg) {
        logArea.append(msg + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    private void refreshTableList() {
        String selected = tableList.getSelectedValue();
        tableListModel.clear();
        for (String name : db.getTables().keySet()) tableListModel.addElement(name);
        if (selected != null && tableListModel.contains(selected)) {
            tableList.setSelectedValue(selected, true);
        }
    }

    private boolean saveDb() {
        try {
            StorageManager.save(db);
            statusLabel.setText("БД сохранена");
            return true;
        } catch (IOException e) {
            statusLabel.setText("Ошибка сохранения: " + e.getMessage());
            JOptionPane.showMessageDialog(this,
                    "Не удалось сохранить БД: " + e.getMessage(),
                    "Ошибка", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    private void onExit() {
        int ans = JOptionPane.showConfirmDialog(this,
                "Сохранить БД перед выходом?", "Выход",
                JOptionPane.YES_NO_CANCEL_OPTION);
        if (ans == JOptionPane.CANCEL_OPTION || ans == JOptionPane.CLOSED_OPTION) return;
        if (ans == JOptionPane.YES_OPTION && !saveDb()) return;
        dispose();
        System.exit(0);
    }
}