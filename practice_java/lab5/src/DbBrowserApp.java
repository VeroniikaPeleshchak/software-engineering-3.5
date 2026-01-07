import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.tree.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;

public class DbBrowserApp {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}
            MainFrame frame = new MainFrame();
            frame.setVisible(true);
        });
    }

    // ========================= DB MANAGER ==============================
    static class DBManager {
        private Connection connection;
        // прапорець "є незбережені зміни"
        private boolean dirty = false;

        public void connect(String filePath) throws SQLException {
            try {
                Class.forName("org.sqlite.JDBC");
            } catch (ClassNotFoundException e) {
                throw new SQLException("SQLite JDBC driver not found!");
            }

            disconnect();
            String url = "jdbc:sqlite:" + filePath;
            connection = DriverManager.getConnection(url);
            // працюємо в ручному режимі комітів
            connection.setAutoCommit(false);
            dirty = false;
        }

        public void disconnect() {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException ignored) {}
                connection = null;
                dirty = false;
            }
        }

        public boolean isConnected() {
            return connection != null;
        }

        public Connection getConnection() {
            return connection;
        }

        public boolean isDirty() {
            return dirty;
        }

        public void markDirty() {
            dirty = true;
        }

        public void commitChanges() throws SQLException {
            if (connection != null) {
                connection.commit();
                dirty = false;
            }
        }

        public void rollbackChanges() throws SQLException {
            if (connection != null) {
                connection.rollback();
                dirty = false;
            }
        }
    }

    // Simple wrapper for table/view node
    static class TableNode {
        private final String name;
        public TableNode(String name) { this.name = name; }
        public String getName() { return name; }
        @Override public String toString() { return name; }
    }

    // Simple node for table components
    static class InfoNode {
        private final String type;
        private final String tableName;
        public InfoNode(String type, String tableName) {
            this.type = type;
            this.tableName = tableName;
        }
        public String getType() { return type; }
        public String getTableName() { return tableName; }
        @Override public String toString() { return type; }
    }

    // Column metadata helper
    static class ColumnInfo {
        String name;
        String type;
        boolean primaryKey;

        ColumnInfo(String name, String type, boolean primaryKey) {
            this.name = name;
            this.type = type;
            this.primaryKey = primaryKey;
        }
    }

    // ======================== TREE RENDERER (ICONS) ============================
    static class SchemaTreeRenderer extends DefaultTreeCellRenderer {
        private final Icon dbIcon;
        private final Icon tablesIcon;
        private final Icon viewsIcon;
        private final Icon tableIcon;
        private final Icon columnsIcon;
        private final Icon constraintsIcon;
        private final Icon indexesIcon;
        private final Icon triggersIcon;

        public SchemaTreeRenderer() {
            dbIcon = createDotIcon(new Color(52, 152, 219));         // синій
            tablesIcon = createDotIcon(new Color(46, 204, 113));     // зелений
            viewsIcon = createDotIcon(new Color(155, 89, 182));      // фіолетовий
            tableIcon = createDotIcon(new Color(230, 126, 34));      // помаранчевий
            columnsIcon = createDotIcon(new Color(241, 196, 15));    // жовтий
            constraintsIcon = createDotIcon(new Color(231, 76, 60)); // червоний
            indexesIcon = createDotIcon(new Color(149, 165, 166));   // сірий
            triggersIcon = createDotIcon(new Color(52, 73, 94));     // темний
        }

        private static Icon createDotIcon(Color color) {
            return new Icon() {
                private final int size = 10;
                @Override
                public void paintIcon(Component c, Graphics g, int x, int y) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(color);
                    g2.fillOval(x, y, size, size);
                    g2.setColor(new Color(0, 0, 0, 60));
                    g2.drawOval(x, y, size, size);
                    g2.dispose();
                }
                @Override public int getIconWidth() { return size; }
                @Override public int getIconHeight() { return size; }
            };
        }

        @Override
        public Component getTreeCellRendererComponent(
                JTree tree, Object value, boolean sel, boolean expanded,
                boolean leaf, int row, boolean hasFocus) {

            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);

            DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
            Object obj = node.getUserObject();

            if (obj instanceof String) {
                String text = (String) obj;
                if ("Database".equals(text) || "No connection".equals(text)) {
                    setIcon(dbIcon);
                } else if ("Tables".equals(text)) {
                    setIcon(tablesIcon);
                } else if ("Views".equals(text)) {
                    setIcon(viewsIcon);
                }
            } else if (obj instanceof TableNode) {
                setIcon(tableIcon);
            } else if (obj instanceof InfoNode) {
                String type = ((InfoNode) obj).getType();
                switch (type) {
                    case "Columns":
                        setIcon(columnsIcon);
                        break;
                    case "Constraints":
                        setIcon(constraintsIcon);
                        break;
                    case "Indexes":
                        setIcon(indexesIcon);
                        break;
                    case "Triggers":
                        setIcon(triggersIcon);
                        break;
                }
            }

            return this;
        }
    }

    // ============================== MAIN FRAME ===============================
    static class MainFrame extends JFrame {
        private final JTextField searchField;

        private final DBManager dbManager = new DBManager();
        private final JTree schemaTree;
        private final DefaultMutableTreeNode rootNode;
        private final DefaultTreeModel treeModel;

        private final JTable dataTable;
        private final JTextArea textArea;

        private String currentTableName = null;

        public MainFrame() {
            super("DB Browser (SQLite, JDBC)");

            // хочемо контролювати закриття вікна (щоб питати про збереження)
            setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
            setSize(1100, 650);
            setLocationRelativeTo(null);

            // left panel - tree
            rootNode = new DefaultMutableTreeNode("No connection");
            treeModel = new DefaultTreeModel(rootNode);
            schemaTree = new JTree(treeModel);
            schemaTree.setRootVisible(true);
            schemaTree.setCellRenderer(new SchemaTreeRenderer());

            JScrollPane treeScroll = new JScrollPane(schemaTree);

            // right panel - data or text
            dataTable = new JTable();
            enableHighlighting();
            JScrollPane tableScroll = new JScrollPane(dataTable);

            textArea = new JTextArea();
            textArea.setEditable(false);
            textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            JScrollPane textScroll = new JScrollPane(textArea);

            // Right panel uses CardLayout: table vs text
            JPanel rightCards = new JPanel(new CardLayout());
            rightCards.add(tableScroll, "table");
            rightCards.add(textScroll, "text");

            // Top bar with Execute button (icon ▶) + search
            JPanel topBar = new JPanel(new BorderLayout());
            topBar.setBorder(new EmptyBorder(5, 5, 5, 5));

            // searchField зліва
            searchField = new JTextField(15);
            searchField.setToolTipText("Type to search...");

            searchField.getDocument().addDocumentListener(new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent e) { onSearchTextChanged(); }

                @Override
                public void removeUpdate(DocumentEvent e) { onSearchTextChanged(); }

                @Override
                public void changedUpdate(DocumentEvent e) { onSearchTextChanged(); }
            });

            JPanel leftSearchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            leftSearchPanel.add(new JLabel("Search: "));
            leftSearchPanel.add(searchField);
            topBar.add(leftSearchPanel, BorderLayout.WEST);

            // кнопка ▶ справа
            JButton execButton = new JButton("▶");
            execButton.setToolTipText("Execute SQL...");
            execButton.addActionListener(e -> openSQLExecutor());
            JPanel rightButtonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
            rightButtonPanel.add(execButton);
            topBar.add(rightButtonPanel, BorderLayout.EAST);

            // Right container: topBar + cards
            JPanel rightContainer = new JPanel(new BorderLayout());
            rightContainer.add(topBar, BorderLayout.NORTH);
            rightContainer.add(rightCards, BorderLayout.CENTER);

            JSplitPane split = new JSplitPane(
                    JSplitPane.HORIZONTAL_SPLIT,
                    treeScroll,
                    rightContainer
            );
            split.setDividerLocation(270);
            getContentPane().add(split);

            // menu & context
            createMenuBar();
            createTreeListeners(rightCards);
            createTreeContextMenu();
            createTableContextMenu();

            // гаряча клавіша Cmd/Ctrl+S
            installSaveKeyBinding();

            // слухач закриття вікна
            addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    attemptExit();
                }
            });
        }

        // ======================== MENU BAR ==================================
        private void createMenuBar() {
            JMenuBar menuBar = new JMenuBar();

            // ---- DATABASE ----
            JMenu db = new JMenu("Database");
            JMenuItem connect = new JMenuItem("Connect");
            JMenuItem disconnect = new JMenuItem("Disconnect");
            JMenuItem execSQL = new JMenuItem("Execute SQL...");
            JMenuItem save = new JMenuItem("Save");
            JMenuItem exit = new JMenuItem("Exit");

            connect.addActionListener(e -> onConnect());
            disconnect.addActionListener(e -> onDisconnect());
            execSQL.addActionListener(e -> openSQLExecutor());
            save.addActionListener(e -> onSave());
            exit.addActionListener(e -> attemptExit());

            // Ctrl/Cmd+S для пункту меню
            int mask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
            save.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, mask));

            db.add(connect);
            db.add(disconnect);
            db.add(execSQL);
            db.add(save);
            db.addSeparator();
            db.add(exit);

            // ---- TABLE ----
            JMenu tbl = new JMenu("Table");
            JMenuItem insertRow = new JMenuItem("Insert row");
            JMenuItem editRow = new JMenuItem("Edit row");
            JMenuItem delRow = new JMenuItem("Delete row");
            JMenuItem renameTable = new JMenuItem("Rename table");
            JMenuItem deleteTable = new JMenuItem("Delete table");

            insertRow.addActionListener(e -> onInsert());
            editRow.addActionListener(e -> onEdit());
            delRow.addActionListener(e -> onDelete());
            renameTable.addActionListener(e -> onRenameTable());
            deleteTable.addActionListener(e -> onDeleteTableStruct());

            tbl.add(insertRow);
            tbl.add(editRow);
            tbl.add(delRow);
            tbl.addSeparator();
            tbl.add(renameTable);
            tbl.add(deleteTable);

            // ---- HELP ----
            JMenu help = new JMenu("Help");
            JMenuItem dbMeta = new JMenuItem("DatabaseMetadata");
            JMenuItem rsMeta = new JMenuItem("ResultSetMetadata");
            JMenuItem about = new JMenuItem("About");

            dbMeta.addActionListener(e -> showDatabaseMetadata());
            rsMeta.addActionListener(e -> showResultSetMetadata());
            about.addActionListener(e -> showAbout());

            help.add(dbMeta);
            help.add(rsMeta);
            help.addSeparator();
            help.add(about);

            menuBar.add(db);
            menuBar.add(tbl);
            // меню Search видалено
            menuBar.add(help);

            setJMenuBar(menuBar);
        }

        // ======================= SAVE / EXIT / DIRTY ===========================

        // загальний метод для обробки незбережених змін
        private boolean ensureSavedOrDiscarded() {
            if (!dbManager.isConnected() || !dbManager.isDirty()) {
                return true;
            }

            int result = JOptionPane.showConfirmDialog(
                    this,
                    "You have unsaved changes. Save them now?",
                    "Unsaved changes",
                    JOptionPane.YES_NO_CANCEL_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );

            if (result == JOptionPane.CANCEL_OPTION || result == JOptionPane.CLOSED_OPTION) {
                return false;
            }

            try {
                if (result == JOptionPane.YES_OPTION) {
                    dbManager.commitChanges();
                } else if (result == JOptionPane.NO_OPTION) {
                    dbManager.rollbackChanges();
                }
                return true;
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                return false;
            }
        }

        private void attemptExit() {
            if (!ensureSavedOrDiscarded()) {
                return;
            }
            dispose();
            System.exit(0);
        }

        private void onSave() {
            if (!dbManager.isConnected()) {
                JOptionPane.showMessageDialog(this, "No database connection.");
                return;
            }
            if (!dbManager.isDirty()) {
                JOptionPane.showMessageDialog(this, "No changes to save.");
                return;
            }
            try {
                dbManager.commitChanges();
                JOptionPane.showMessageDialog(this, "Changes saved.");
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }

        private void installSaveKeyBinding() {
            int mask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
            KeyStroke saveStroke = KeyStroke.getKeyStroke(KeyEvent.VK_S, mask);

            JRootPane rootPane = getRootPane();
            rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                    .put(saveStroke, "saveCommand");
            rootPane.getActionMap()
                    .put("saveCommand", new AbstractAction() {
                        @Override
                        public void actionPerformed(ActionEvent e) {
                            onSave();
                        }
                    });
        }

        // ======================= CONNECT / DISCONNECT ===========================
        private void onConnect() {
            // якщо вже є підключення і незбережені зміни – питаємо
            if (dbManager.isConnected()) {
                if (!ensureSavedOrDiscarded()) {
                    return;
                }
            }

            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Choose SQLite database file");

            chooser.setFileFilter(new FileNameExtensionFilter(
                    "SQLite Databases (*.db, *.sqlite, *.sqlite3)",
                    "db", "sqlite", "sqlite3"
            ));

            chooser.setAcceptAllFileFilterUsed(false);

            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                try {
                    dbManager.connect(chooser.getSelectedFile().getAbsolutePath());
                    setTitle("DB Browser – " + chooser.getSelectedFile().getName());
                    loadTree();
                    JOptionPane.showMessageDialog(this, "Connected!");
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }

        private void onDisconnect() {
            if (!ensureSavedOrDiscarded()) {
                return;
            }

            dbManager.disconnect();
            rootNode.removeAllChildren();
            rootNode.setUserObject("No connection");
            treeModel.reload();
            currentTableName = null;
            dataTable.setModel(new DefaultTableModel());
            textArea.setText("");
        }

        // =========================== TREE LOADING ===============================
        private void loadTree() {
            rootNode.removeAllChildren();
            rootNode.setUserObject("Database");

            DefaultMutableTreeNode tablesNode = new DefaultMutableTreeNode("Tables");
            DefaultMutableTreeNode viewsNode  = new DefaultMutableTreeNode("Views");

            rootNode.add(tablesNode);
            rootNode.add(viewsNode);

            try {
                DatabaseMetaData meta = dbManager.getConnection().getMetaData();

                // Tables
                ResultSet rs = meta.getTables(null, null, "%", new String[]{"TABLE"});
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    DefaultMutableTreeNode t = new DefaultMutableTreeNode(new TableNode(tableName));

                    t.add(new DefaultMutableTreeNode(new InfoNode("Columns", tableName)));
                    t.add(new DefaultMutableTreeNode(new InfoNode("Constraints", tableName)));
                    t.add(new DefaultMutableTreeNode(new InfoNode("Indexes", tableName)));
                    t.add(new DefaultMutableTreeNode(new InfoNode("Triggers", tableName)));

                    tablesNode.add(t);
                }
                rs.close();

                // Views
                rs = meta.getTables(null, null, "%", new String[]{"VIEW"});
                while (rs.next()) {
                    viewsNode.add(new DefaultMutableTreeNode(new TableNode(rs.getString("TABLE_NAME"))));
                }
                rs.close();

            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage());
            }

            treeModel.reload();
            expandAll(schemaTree);
        }

        private void expandAll(JTree tree) {
            for (int i = 0; i < tree.getRowCount(); i++) tree.expandRow(i);
        }

        // ====================== LISTENERS + DISPLAY LOGIC ======================
        private void createTreeListeners(JPanel rightCards) {

            schemaTree.addTreeSelectionListener(e -> {
                DefaultMutableTreeNode node =
                        (DefaultMutableTreeNode) schemaTree.getLastSelectedPathComponent();
                if (node == null) return;

                Object obj = node.getUserObject();
                CardLayout cl = (CardLayout) rightCards.getLayout();

                if (obj instanceof TableNode) {
                    String name = ((TableNode) obj).getName();
                    currentTableName = name;

                    // СКИДАЄМО ПОШУК, щоб не фільтрував порожньою таблицею
                    if (searchField != null) {
                        searchField.setText("");
                    }

                    loadTable(name);
                    cl.show(rightCards, "table");
                    return;
                }

                if (obj instanceof InfoNode) {
                    InfoNode info = (InfoNode) obj;
                    showInfo(info.getType(), info.getTableName());
                    cl.show(rightCards, "text");
                }
            });
        }

        // Show table/view content in JTable
        private void loadTable(String table) {
            if (!dbManager.isConnected()) return;

            try {
                String q = "SELECT * FROM " + table;
                Statement st = dbManager.getConnection().createStatement();
                ResultSet rs = st.executeQuery(q);

                dataTable.setModel(buildTableModel(rs));

                rs.close();
                st.close();
            } catch (Exception ex) {
                textArea.setText(ex.getMessage());
            }
        }

        // Show text info (columns, indexes, triggers…)
        private void showInfo(String type, String table) {
            StringBuilder sb = new StringBuilder();

            try {
                switch (type) {
                    case "Columns":
                        sb.append("COLUMNS of ").append(table).append(":\n\n");
                        ResultSet rs = dbManager.getConnection().createStatement()
                                .executeQuery("PRAGMA table_info(" + table + ");");
                        while (rs.next()) {
                            sb.append(rs.getString("name"))
                                    .append("  ")
                                    .append(rs.getString("type"))
                                    .append(rs.getInt("pk") == 1 ? "  PRIMARY KEY" : "")
                                    .append("\n");
                        }
                        break;

                    case "Constraints":
                        sb.append("CONSTRAINTS of ").append(table).append(":\n\n");
                        ResultSet fk = dbManager.getConnection().createStatement()
                                .executeQuery("PRAGMA foreign_key_list(" + table + ");");
                        while (fk.next()) {
                            sb.append("FOREIGN KEY ")
                                    .append(fk.getString("from"))
                                    .append(" REFERENCES ")
                                    .append(fk.getString("table"))
                                    .append("(").append(fk.getString("to")).append(")")
                                    .append("\n");
                        }
                        break;

                    case "Indexes":
                        sb.append("INDEXES of ").append(table).append(":\n\n");
                        ResultSet ix = dbManager.getConnection().createStatement()
                                .executeQuery("PRAGMA index_list(" + table + ");");
                        while (ix.next()) {
                            sb.append(ix.getString("name"));
                            sb.append(ix.getInt("unique") == 1 ? " (UNIQUE)" : "");
                            sb.append("\n");
                        }
                        break;

                    case "Triggers":
                        sb.append("TRIGGERS of ").append(table).append(":\n\n");
                        ResultSet triggers = dbManager.getConnection().createStatement()
                                .executeQuery("SELECT name, sql FROM sqlite_master " +
                                        "WHERE type='trigger' AND tbl_name='" + table + "';");
                        while (triggers.next()) {
                            sb.append(triggers.getString("name")).append("\n")
                                    .append(triggers.getString("sql")).append("\n\n");
                        }
                        break;
                }
            } catch (SQLException ex) {
                sb.append("Error: ").append(ex.getMessage());
            }

            textArea.setText(sb.toString());
        }

        // Build table model from ResultSet
        private DefaultTableModel buildTableModel(ResultSet rs) throws SQLException {
            ResultSetMetaData meta = rs.getMetaData();
            int cols = meta.getColumnCount();

            Vector<String> colNames = new Vector<>();
            for (int i = 1; i <= cols; i++) colNames.add(meta.getColumnName(i));

            Vector<Vector<Object>> data = new Vector<>();
            while (rs.next()) {
                Vector<Object> row = new Vector<>();
                for (int i = 1; i <= cols; i++) row.add(rs.getObject(i));
                data.add(row);
            }
            return new DefaultTableModel(data, colNames);
        }

        // ============================== SEARCH ==================================
        private void onSearchTextChanged() {
            if (!dbManager.isConnected() || currentTableName == null) {
                return;
            }

            String pattern = searchField.getText().trim();
            if (pattern.isEmpty()) {
                // якщо пусто – показуємо всю таблицю, без фільтра
                loadTable(currentTableName);
                return;
            }

            try {
                List<ColumnInfo> cols = getColumns(currentTableName);

                StringBuilder sb = new StringBuilder("SELECT * FROM ");
                sb.append(currentTableName).append(" WHERE ");

                boolean first = true;
                for (ColumnInfo c : cols) {
                    // пропускаємо id
                    if ("id".equalsIgnoreCase(c.name)) continue;

                    if (!first) sb.append(" OR ");
                    sb.append(c.name).append(" LIKE ?");
                    first = false;
                }

                // якщо всі колонки — тільки id (або нічого)
                if (first) {
                    loadTable(currentTableName);
                    return;
                }

                PreparedStatement ps = dbManager.getConnection().prepareStatement(sb.toString());
                int idx = 1;
                for (ColumnInfo c : cols) {
                    if ("id".equalsIgnoreCase(c.name)) continue;
                    ps.setString(idx++, "%" + pattern + "%");
                }

                ResultSet rs = ps.executeQuery();
                dataTable.setModel(buildTableModel(rs));

            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage());
            }
        }

        private void enableHighlighting() {
            dataTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
                @Override
                public Component getTableCellRendererComponent(
                        JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column)
                {
                    Component c = super.getTableCellRendererComponent(
                            table, value, isSelected, hasFocus, row, column);

                    String search = searchField.getText().trim().toLowerCase();
                    if (!search.isEmpty() && value != null) {
                        String text = value.toString().toLowerCase();
                        if (text.contains(search)) {
                            c.setBackground(new Color(255, 255, 150)); // жовта підсвітка
                        } else {
                            c.setBackground(Color.WHITE);
                        }
                    } else {
                        c.setBackground(Color.WHITE);
                    }

                    if (isSelected) c.setBackground(new Color(184, 207, 229));

                    return c;
                }
            });
        }

        // ============================= SQL EXECUTOR ================================
        private void openSQLExecutor() {
            if (!dbManager.isConnected()) {
                JOptionPane.showMessageDialog(this, "Connect to database first!");
                return;
            }

            JTextArea ta = new JTextArea(10, 40);

            int result = JOptionPane.showConfirmDialog(
                    this, new JScrollPane(ta), "Execute SQL",
                    JOptionPane.OK_CANCEL_OPTION
            );

            if (result == JOptionPane.OK_OPTION) {
                String sql = ta.getText().trim();
                if (sql.isEmpty()) {
                    // нічого не ввели → просто вийшли
                    return;
                }

                try {
                    Statement st = dbManager.getConnection().createStatement();
                    boolean hasResult = st.execute(sql);

                    if (hasResult) {
                        ResultSet rs = st.getResultSet();
                        dataTable.setModel(buildTableModel(rs));
                    } else {
                        // DDL/UPDATE/INSERT/DELETE → вважаємо, що є незбережені зміни
                        dbManager.markDirty();
                        // якщо змінився список таблиць – оновимо дерево
                        loadTree();
                        JOptionPane.showMessageDialog(this, "Executed.");
                    }

                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, ex.getMessage());
                }
            }
        }

        // ====================== AUTOMATIC INSERT / EDIT / DELETE ===================

        private List<ColumnInfo> getColumns(String table) throws SQLException {
            List<ColumnInfo> cols = new ArrayList<>();
            Statement st = dbManager.getConnection().createStatement();
            ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ");");
            while (rs.next()) {
                String name = rs.getString("name");
                String type = rs.getString("type");
                boolean pk = rs.getInt("pk") == 1;
                cols.add(new ColumnInfo(name, type, pk));
            }
            rs.close();
            st.close();
            return cols;
        }

        private void onInsert() {
            if (!dbManager.isConnected() || currentTableName == null) {
                JOptionPane.showMessageDialog(this, "Select a table first!");
                return;
            }

            try {
                List<ColumnInfo> cols = getColumns(currentTableName);

                JPanel panel = new JPanel(new GridLayout(0, 2, 5, 5));
                panel.setBorder(new EmptyBorder(10, 10, 10, 10));
                List<ColumnInfo> usedCols = new ArrayList<>();
                List<JTextField> fields = new ArrayList<>();

                for (ColumnInfo c : cols) {
                    boolean isAutoPk = c.primaryKey && c.type != null &&
                            c.type.toUpperCase().contains("INT");
                    if (isAutoPk) continue;

                    usedCols.add(c);
                    panel.add(new JLabel(c.name + " (" + c.type + "):"));
                    JTextField tf = new JTextField(20);
                    fields.add(tf);
                    panel.add(tf);
                }

                if (usedCols.isEmpty()) {
                    JOptionPane.showMessageDialog(this, "No insertable columns.");
                    return;
                }

                int res = JOptionPane.showConfirmDialog(this, panel,
                        "Insert into " + currentTableName,
                        JOptionPane.OK_CANCEL_OPTION,
                        JOptionPane.PLAIN_MESSAGE);

                if (res != JOptionPane.OK_OPTION) return;

                boolean allEmpty = true;
                for (JTextField tf : fields) {
                    if (!tf.getText().trim().isEmpty()) {
                        allEmpty = false;
                        break;
                    }
                }
                if (allEmpty) {
                    // нічого не ввели – не робимо insert і не показуємо помилок
                    return;
                }

                StringBuilder sb = new StringBuilder("INSERT INTO ");
                sb.append(currentTableName).append(" (");
                for (int i = 0; i < usedCols.size(); i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(usedCols.get(i).name);
                }
                sb.append(") VALUES (");
                for (int i = 0; i < usedCols.size(); i++) {
                    if (i > 0) sb.append(", ");
                    sb.append("?");
                }
                sb.append(");");

                PreparedStatement ps = dbManager.getConnection().prepareStatement(sb.toString());
                for (int i = 0; i < usedCols.size(); i++) {
                    ColumnInfo c = usedCols.get(i);
                    String text = fields.get(i).getText().trim();
                    setParamByType(ps, i + 1, c.type, text);
                }

                int count = ps.executeUpdate();
                dbManager.markDirty();
                JOptionPane.showMessageDialog(this, "Inserted rows: " + count);
                loadTable(currentTableName);

            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage());
            }
        }

        private void onEdit() {
            if (!dbManager.isConnected() || currentTableName == null) {
                JOptionPane.showMessageDialog(this, "Select a table first!");
                return;
            }

            int row = dataTable.getSelectedRow();
            if (row == -1) {
                JOptionPane.showMessageDialog(this, "Select a row in the table to edit.");
                return;
            }

            try {
                List<ColumnInfo> cols = getColumns(currentTableName);

                List<ColumnInfo> pkCols = new ArrayList<>();
                for (ColumnInfo c : cols) {
                    if (c.primaryKey) pkCols.add(c);
                }
                if (pkCols.isEmpty()) {
                    JOptionPane.showMessageDialog(this,
                            "Edit works only for tables with PRIMARY KEY.");
                    return;
                }

                DefaultTableModel model = (DefaultTableModel) dataTable.getModel();

                JPanel panel = new JPanel(new GridLayout(0, 2, 5, 5));
                panel.setBorder(new EmptyBorder(10, 10, 10, 10));

                List<ColumnInfo> editableCols = new ArrayList<>();
                List<JTextField> fields = new ArrayList<>();

                for (ColumnInfo c : cols) {
                    if (c.primaryKey) continue;
                    editableCols.add(c);
                    panel.add(new JLabel(c.name + " (" + c.type + "):"));
                    int colIndex = model.findColumn(c.name);
                    Object currentVal = model.getValueAt(row, colIndex);
                    JTextField tf = new JTextField(currentVal == null ? "" : currentVal.toString(), 20);
                    fields.add(tf);
                    panel.add(tf);
                }

                int res = JOptionPane.showConfirmDialog(this, panel,
                        "Edit row in " + currentTableName,
                        JOptionPane.OK_CANCEL_OPTION,
                        JOptionPane.PLAIN_MESSAGE);

                if (res != JOptionPane.OK_OPTION) return;

                StringBuilder sb = new StringBuilder("UPDATE ");
                sb.append(currentTableName).append(" SET ");
                for (int i = 0; i < editableCols.size(); i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(editableCols.get(i).name).append(" = ?");
                }
                sb.append(" WHERE ");
                for (int i = 0; i < pkCols.size(); i++) {
                    if (i > 0) sb.append(" AND ");
                    sb.append(pkCols.get(i).name).append(" = ?");
                }

                PreparedStatement ps = dbManager.getConnection().prepareStatement(sb.toString());

                int paramIndex = 1;
                for (int i = 0; i < editableCols.size(); i++) {
                    ColumnInfo c = editableCols.get(i);
                    String text = fields.get(i).getText().trim();
                    setParamByType(ps, paramIndex++, c.type, text);
                }
                for (ColumnInfo c : pkCols) {
                    int colIndex = model.findColumn(c.name);
                    Object pkVal = model.getValueAt(row, colIndex);
                    setParamObject(ps, paramIndex++, c.type, pkVal);
                }

                int count = ps.executeUpdate();
                dbManager.markDirty();
                JOptionPane.showMessageDialog(this, "Updated rows: " + count);
                loadTable(currentTableName);

            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage());
            }
        }

        private void onDelete() {
            if (!dbManager.isConnected() || currentTableName == null) {
                JOptionPane.showMessageDialog(this, "Select a table first!");
                return;
            }

            int row = dataTable.getSelectedRow();
            if (row == -1) {
                JOptionPane.showMessageDialog(this, "Select a row in the table to delete.");
                return;
            }

            int confirm = JOptionPane.showConfirmDialog(this,
                    "Delete selected row?", "Confirm delete",
                    JOptionPane.YES_NO_OPTION);
            if (confirm != JOptionPane.YES_OPTION) return;

            try {
                List<ColumnInfo> cols = getColumns(currentTableName);
                List<ColumnInfo> pkCols = new ArrayList<>();
                for (ColumnInfo c : cols) {
                    if (c.primaryKey) pkCols.add(c);
                }
                if (pkCols.isEmpty()) {
                    JOptionPane.showMessageDialog(this,
                            "Delete works only for tables with PRIMARY KEY.");
                    return;
                }

                DefaultTableModel model = (DefaultTableModel) dataTable.getModel();

                StringBuilder sb = new StringBuilder("DELETE FROM ");
                sb.append(currentTableName).append(" WHERE ");
                for (int i = 0; i < pkCols.size(); i++) {
                    if (i > 0) sb.append(" AND ");
                    sb.append(pkCols.get(i).name).append(" = ?");
                }

                PreparedStatement ps = dbManager.getConnection().prepareStatement(sb.toString());
                int paramIndex = 1;
                for (ColumnInfo c : pkCols) {
                    int colIndex = model.findColumn(c.name);
                    Object pkVal = model.getValueAt(row, colIndex);
                    setParamObject(ps, paramIndex++, c.type, pkVal);
                }

                int count = ps.executeUpdate();
                dbManager.markDirty();
                JOptionPane.showMessageDialog(this, "Deleted rows: " + count);
                loadTable(currentTableName);

            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage());
            }
        }

        // Rename table
        private void onRenameTable() {
            if (!dbManager.isConnected() || currentTableName == null) {
                JOptionPane.showMessageDialog(this, "Select a table first!");
                return;
            }

            String newName = JOptionPane.showInputDialog(this,
                    "Enter new name for table:",
                    currentTableName);

            if (newName == null) return;
            newName = newName.trim();
            if (newName.isEmpty()) return;

            String oldName = currentTableName;

            if (newName.equalsIgnoreCase(oldName)) {
                return;
            }

            try {
                String sql = "ALTER TABLE " + oldName + " RENAME TO " + newName + ";";
                Statement st = dbManager.getConnection().createStatement();
                st.executeUpdate(sql);
                st.close();

                dbManager.markDirty();
                currentTableName = newName;
                loadTree();
                loadTable(newName);

                JOptionPane.showMessageDialog(this, "Table renamed from " + oldName + " to " + newName);
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }


        // Delete table (DROP TABLE)
        private void onDeleteTableStruct() {
            if (!dbManager.isConnected() || currentTableName == null) {
                JOptionPane.showMessageDialog(this, "Select a table first!");
                return;
            }

            int confirm = JOptionPane.showConfirmDialog(this,
                    "Delete table '" + currentTableName + "'? All data will be lost.",
                    "Delete table",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);

            if (confirm != JOptionPane.YES_OPTION) return;

            String tableToDelete = currentTableName;

            try {
                String sql = "DROP TABLE " + tableToDelete + ";";
                Statement st = dbManager.getConnection().createStatement();
                st.executeUpdate(sql);
                st.close();

                dbManager.markDirty();
                currentTableName = null;
                dataTable.setModel(new DefaultTableModel());
                loadTree();

                JOptionPane.showMessageDialog(this, "Table '" + tableToDelete + "' deleted.");
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }

        private void setParamByType(PreparedStatement ps, int index, String sqlType, String text) throws SQLException {
            if (text == null || text.isEmpty()) {
                ps.setObject(index, null);
                return;
            }
            String t = sqlType == null ? "" : sqlType.toUpperCase();
            try {
                if (t.contains("INT")) {
                    ps.setInt(index, Integer.parseInt(text));
                } else if (t.contains("REAL") || t.contains("DOUBLE") || t.contains("FLOAT")) {
                    ps.setDouble(index, Double.parseDouble(text));
                } else {
                    ps.setString(index, text);
                }
            } catch (NumberFormatException e) {
                ps.setString(index, text);
            }
        }

        private void setParamObject(PreparedStatement ps, int index, String sqlType, Object value) throws SQLException {
            if (value == null) {
                ps.setObject(index, null);
                return;
            }
            String t = sqlType == null ? "" : sqlType.toUpperCase();
            if (value instanceof Number) {
                if (t.contains("REAL") || t.contains("DOUBLE") || t.contains("FLOAT")) {
                    ps.setDouble(index, ((Number) value).doubleValue());
                } else {
                    ps.setLong(index, ((Number) value).longValue());
                }
            } else {
                ps.setString(index, value.toString());
            }
        }

        // =========================== CONTEXT MENU (TREE) ================================
        private void createTreeContextMenu() {
            JPopupMenu menu = new JPopupMenu();

            JMenuItem renameTable = new JMenuItem("Rename table");
            JMenuItem deleteTable = new JMenuItem("Delete table");
            JMenuItem sql = new JMenuItem("Execute SQL...");


            renameTable.addActionListener(e -> onRenameTable());
            deleteTable.addActionListener(e -> onDeleteTableStruct());
            sql.addActionListener(e -> openSQLExecutor());


            menu.add(renameTable);
            menu.add(deleteTable);
            menu.addSeparator();
            menu.add(sql);

            schemaTree.addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) { show(e); }

                @Override
                public void mouseReleased(MouseEvent e) { show(e); }

                private void show(MouseEvent e) {
                    if (!e.isPopupTrigger()) return;

                    TreePath path = schemaTree.getPathForLocation(e.getX(), e.getY());
                    if (path == null) return;

                    schemaTree.setSelectionPath(path);
                    Object node = ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();

                    if (node instanceof TableNode) {
                        currentTableName = ((TableNode) node).getName();
                        menu.show(schemaTree, e.getX(), e.getY());
                    }
                }
            });
        }

        // ====================== CONTEXT MENU (TABLE DATA) ==============================
        private void createTableContextMenu() {
            JPopupMenu rowMenu = new JPopupMenu();

            JMenuItem insertRow = new JMenuItem("Insert row");
            JMenuItem editRow = new JMenuItem("Edit row");
            JMenuItem deleteRow = new JMenuItem("Delete row");

            insertRow.addActionListener(e -> onInsert());
            editRow.addActionListener(e -> onEdit());
            deleteRow.addActionListener(e -> onDelete());

            rowMenu.add(insertRow);
            rowMenu.add(editRow);
            rowMenu.add(deleteRow);

            dataTable.addMouseListener(new MouseAdapter() {
                private void showMenu(MouseEvent e) {
                    if (!e.isPopupTrigger()) return;
                    if (!dbManager.isConnected() || currentTableName == null) return;

                    int row = dataTable.rowAtPoint(e.getPoint());
                    if (row != -1) {
                        dataTable.setRowSelectionInterval(row, row);
                    }

                    rowMenu.show(dataTable, e.getX(), e.getY());
                }

                @Override
                public void mousePressed(MouseEvent e) { showMenu(e); }

                @Override
                public void mouseReleased(MouseEvent e) { showMenu(e); }
            });
        }

        // ================================ HELP ==================================

        private void showDatabaseMetadata() {
            if (!dbManager.isConnected()) {
                JOptionPane.showMessageDialog(this, "Connect to database first!");
                return;
            }
            try {
                DatabaseMetaData meta = dbManager.getConnection().getMetaData();
                StringBuilder sb = new StringBuilder();
                sb.append("Database Product Name: ").append(meta.getDatabaseProductName()).append("\n");
                sb.append("Database Product Version: ").append(meta.getDatabaseProductVersion()).append("\n");
                sb.append("Driver Name: ").append(meta.getDriverName()).append("\n");
                sb.append("Driver Version: ").append(meta.getDriverVersion()).append("\n");
                sb.append("URL: ").append(meta.getURL()).append("\n");

                JTextArea area = new JTextArea(sb.toString(), 10, 50);
                area.setEditable(false);
                JOptionPane.showMessageDialog(this, new JScrollPane(area),
                        "DatabaseMetadata", JOptionPane.INFORMATION_MESSAGE);

            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }

        private void showResultSetMetadata() {
            if (!dbManager.isConnected() || currentTableName == null) {
                JOptionPane.showMessageDialog(this,
                        "Select a table to view ResultSetMetadata.");
                return;
            }
            String sql = "SELECT * FROM " + currentTableName + " LIMIT 1";
            try (Statement stmt = dbManager.getConnection().createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {

                ResultSetMetaData meta = rs.getMetaData();
                int columnCount = meta.getColumnCount();
                StringBuilder sb = new StringBuilder();
                sb.append("Table: ").append(currentTableName).append("\n\n");
                for (int i = 1; i <= columnCount; i++) {
                    sb.append("Column ").append(i).append(":\n");
                    sb.append("  Name: ").append(meta.getColumnName(i)).append("\n");
                    sb.append("  Type: ").append(meta.getColumnTypeName(i)).append("\n");
                    sb.append("  Size: ").append(meta.getColumnDisplaySize(i)).append("\n");
                    sb.append("  Nullable: ")
                            .append(meta.isNullable(i) == ResultSetMetaData.columnNullable ? "YES" : "NO")
                            .append("\n\n");
                }

                JTextArea area = new JTextArea(sb.toString(), 15, 50);
                area.setEditable(false);
                JOptionPane.showMessageDialog(this, new JScrollPane(area),
                        "ResultSetMetadata", JOptionPane.INFORMATION_MESSAGE);

            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }

        private void showAbout() {
            JOptionPane.showMessageDialog(this,
                    "<html><h3>DB Browser</h3>" +
                            "<p>Автор: Вероніка Пелещак</p>" +
                            "<p>E-mail: <a href='mailto:pelesakveronika@gmail.com'>pelesakveronika@gmail.com</a></p>" +
                            "</html>",
                    "About",
                    JOptionPane.INFORMATION_MESSAGE
            );
        }
    }
}


