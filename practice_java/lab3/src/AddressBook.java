import javax.swing.*;
import javax.swing.event.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;

public class AddressBook {

    static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}
            new AddressBookFrame().setVisible(true);
        });
    }
}

/* --- Data model: Contact --- */
class Contact {
    String firstName;
    String lastName;
    String phone;
    String email;
    String gender;
    List<String> tags;
    boolean favorite;

    Contact(String firstName, String lastName, String phone, String email,
            String gender, List<String> tags, boolean favorite) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.phone = phone;
        this.email = email;
        this.gender = gender;
        this.tags = new ArrayList<>(tags);
        this.favorite = favorite;
    }


    String toCSVLine() {
        return csvEscape(firstName) + "," + csvEscape(lastName) + "," + csvEscape(phone) + "," +
                csvEscape(email) + "," + csvEscape(gender) + "," + csvEscape(String.join(";", tags)) + "," +
                (favorite ? "1" : "0");
    }

    static Contact fromCSVLine(String line) {
        List<String> parts = csvSplit(line);
        if (parts.size() < 7) return null;
        List<String> tags = parts.get(5).isEmpty() ? Collections.emptyList() :
                Arrays.asList(parts.get(5).split(";"));
        boolean fav = "1".equals(parts.get(6));
        return new Contact(parts.get(0), parts.get(1), parts.get(2), parts.get(3), parts.get(4), tags, fav);
    }

    private static String csvEscape(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    private static List<String> csvSplit(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    cur.append(c);
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                } else if (c == ',') {
                    out.add(cur.toString());
                    cur.setLength(0);
                } else {
                    cur.append(c);
                }
            }
        }
        out.add(cur.toString());
        return out;
    }
}

/* --- Table model backed by List<Contact> --- */
class ContactsTableModel extends AbstractTableModel {
    private final String[] columns = {"First Name", "Last Name", "Phone", "Email", "Gender", "Tags", "Favorite"};
    private final List<Contact> contacts;

    ContactsTableModel(List<Contact> contacts) {
        this.contacts = contacts;
    }

    public int getRowCount() { return contacts.size(); }
    public int getColumnCount() { return columns.length; }
    public String getColumnName(int col) { return columns[col]; }

    public Object getValueAt(int row, int col) {
        Contact c = contacts.get(row);
        switch (col) {
            case 0: return c.firstName;
            case 1: return c.lastName;
            case 2: return c.phone;
            case 3: return c.email;
            case 4: return c.gender;
            case 5: return String.join(", ", c.tags);
            case 6: return c.favorite ? "★" : "";
        }
        return "";
    }

    public Class<?> getColumnClass(int col) {
        if (col == 6) return String.class;
        return String.class;
    }

    public boolean isCellEditable(int r, int c) { return false; }

    public Contact getContactAt(int row) { return contacts.get(row); }

    public void addContact(Contact c) {
        contacts.add(c);
        int r = contacts.size()-1;
        fireTableRowsInserted(r, r);
    }

    public void updateContact(int row, Contact c) {
        contacts.set(row, c);
        fireTableRowsUpdated(row, row);
    }

    public void removeContact(int row) {
        contacts.remove(row);
        fireTableRowsDeleted(row, row);
    }

    public List<Contact> getAll() { return Collections.unmodifiableList(contacts); }

    public void clear() {
        int n = contacts.size();
        if (n>0) {
            contacts.clear();
            fireTableRowsDeleted(0, n-1);
        }
    }
}

/* --- Main application frame --- */
class AddressBookFrame extends JFrame {
    private final List<Contact> contacts = new ArrayList<>();
    private final ContactsTableModel tableModel = new ContactsTableModel(contacts);
    private final JTable table = new JTable(tableModel);
    private final TableRowSorter<ContactsTableModel> sorter = new TableRowSorter<>(tableModel);
    private final JLabel statusBar = new JLabel("Ready");
    private final JTextField searchField = new JTextField(20);
    private final JCheckBox showFavoritesCheckbox = new JCheckBox("Show Favorites Only");
    private final JButton addBtn = new JButton("Add");
    private final JButton editBtn = new JButton("Edit");
    private final JButton deleteBtn = new JButton("Delete");
    private final JButton markFavBtn = new JButton("Mark as Favorite");


    AddressBookFrame() {
        super("Address Book");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1000, 600);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        initMenuBar();
        initToolBar();
        initTable();
        initStatusBar();
        initContextMenu();

        applyFilters();
        updateStatus();

        showFavoritesCheckbox.addActionListener(e -> applyFilters());

        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                int r = JOptionPane.showConfirmDialog(AddressBookFrame.this,
                        "Are you sure you want to exit?", "Confirm Exit", JOptionPane.YES_NO_OPTION);
                if (r == JOptionPane.YES_OPTION) {
                    System.exit(0);
                }
            }
        });
    }

    private void initMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        JMenu file = new JMenu("File");
        JMenuItem newItem = new JMenuItem("New");
        JMenuItem saveItem = new JMenuItem("Save");
        JMenuItem loadItem = new JMenuItem("Load");
        JMenuItem exitItem = new JMenuItem("Exit");

        newItem.addActionListener(e -> onNew());
        saveItem.addActionListener(e -> onSave());
        loadItem.addActionListener(e -> onLoad());
        exitItem.addActionListener(e -> dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING)));

        file.add(newItem);
        file.add(saveItem);
        file.add(loadItem);
        file.addSeparator();
        file.add(exitItem);

        menuBar.add(file);

        JMenu help = new JMenu("Help");
        JMenuItem aboutItem = new JMenuItem("About");
        aboutItem.addActionListener(e -> JOptionPane.showMessageDialog(this,
                "Address Book\nSwing Java Application\nFeatures: add/edit/delete, favorites, search, save/load CSV",
                "About", JOptionPane.INFORMATION_MESSAGE));
        help.add(aboutItem);
        menuBar.add(help);

        setJMenuBar(menuBar);
    }


    private void initToolBar() {
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);

        addBtn.setFocusable(false);
        editBtn.setFocusable(false);
        deleteBtn.setFocusable(false);
        markFavBtn.setFocusable(false);

        toolbar.add(addBtn);
        toolbar.add(editBtn);
        toolbar.add(deleteBtn);
        toolbar.add(markFavBtn);
        toolbar.addSeparator();
        toolbar.add(showFavoritesCheckbox);
        toolbar.addSeparator(new Dimension(10,0));
        toolbar.add(new JLabel("Search: "));
        toolbar.add(searchField);

        addBtn.addActionListener(e -> onAdd());
        editBtn.addActionListener(e -> onEdit());
        deleteBtn.addActionListener(e -> onDelete());
        markFavBtn.addActionListener(e -> onMarkFavorite());

        editBtn.setEnabled(false);
        deleteBtn.setEnabled(false);
        markFavBtn.setEnabled(false);

        table.getSelectionModel().addListSelectionListener(e -> {
            boolean sel = table.getSelectedRow() >= 0;
            editBtn.setEnabled(sel);
            deleteBtn.setEnabled(sel);
            markFavBtn.setEnabled(sel);
        });

        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { applyFilters(); }
            public void removeUpdate(DocumentEvent e) { applyFilters(); }
            public void changedUpdate(DocumentEvent e) { applyFilters(); }
        });

        add(toolbar, BorderLayout.NORTH);
    }


    private void initTable() {
        table.setModel(tableModel);
        table.setRowSorter(sorter);
        sorter.setSortable(6, true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFillsViewportHeight(true);
        table.setAutoCreateRowSorter(false);

        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(SwingConstants.CENTER);
        table.getColumnModel().getColumn(6).setCellRenderer(centerRenderer);

        table.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) onEdit();
                if (table.rowAtPoint(e.getPoint()) < 0) table.clearSelection();
            }
        });

        JScrollPane scrollPane = new JScrollPane(table);
        add(scrollPane, BorderLayout.CENTER);
    }

    private void initStatusBar() {
        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.setBorder(BorderFactory.createEmptyBorder(2,6,2,6));
        statusPanel.add(statusBar, BorderLayout.WEST);
        add(statusPanel, BorderLayout.SOUTH);
    }

    private void initContextMenu() {
        JPopupMenu popup = new JPopupMenu();
        JMenuItem editMi = new JMenuItem("Edit");
        JMenuItem delMi = new JMenuItem("Delete");
        JMenuItem favMi = new JMenuItem("Toggle Favorite");

        editMi.addActionListener(e -> onEdit());
        delMi.addActionListener(e -> onDelete());
        favMi.addActionListener(e -> onMarkFavorite());

        popup.add(editMi); popup.add(delMi); popup.addSeparator(); popup.add(favMi);
        table.setComponentPopupMenu(popup);
    }

    /* --- Actions --- */
    private void onNew() {
        int r = JOptionPane.showConfirmDialog(this, "Create new address book? Current data will be lost.", "Confirm New", JOptionPane.YES_NO_OPTION);
        if (r == JOptionPane.YES_OPTION) {
            tableModel.clear();
            updateStatus("New address book (cleared).");
        }
    }

    private void onAdd() {
        ContactDialog dlg = new ContactDialog(this, "Add Contact", null);
        dlg.setVisible(true);
        Contact result = dlg.getResult();
        if (result != null) {
            tableModel.addContact(result);
            updateStatus("Contact added.");
        }
    }

    private void onEdit() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) { JOptionPane.showMessageDialog(this, "Select a contact to edit."); return; }
        int modelRow = table.convertRowIndexToModel(viewRow);
        Contact old = tableModel.getContactAt(modelRow);
        ContactDialog dlg = new ContactDialog(this, "Edit Contact", old);
        dlg.setVisible(true);
        Contact updated = dlg.getResult();
        if (updated != null) {
            tableModel.updateContact(modelRow, updated);
            updateStatus("Contact updated.");
        }
    }

    private void onDelete() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) { JOptionPane.showMessageDialog(this, "Select a contact to delete."); return; }
        int r = JOptionPane.showConfirmDialog(this, "Delete selected contact?", "Confirm Delete", JOptionPane.YES_NO_OPTION);
        if (r != JOptionPane.YES_OPTION) return;
        int modelRow = table.convertRowIndexToModel(viewRow);
        tableModel.removeContact(modelRow);
        updateStatus("Contact deleted.");
    }

    private void onMarkFavorite() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) { JOptionPane.showMessageDialog(this, "Select a contact."); return; }
        int modelRow = table.convertRowIndexToModel(viewRow);
        Contact c = tableModel.getContactAt(modelRow);
        c.favorite = !c.favorite;
        tableModel.fireTableRowsUpdated(modelRow, modelRow);
        updateStatus(c.favorite ? "Marked as favorite." : "Unmarked favorite.");
    }

    private void onSave() {
        JFileChooser fileChooser = new JFileChooser();
        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File f = fileChooser.getSelectedFile();
            try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8))) {
                for (Contact c : tableModel.getAll()) bw.write(c.toCSVLine() + "\n");
                updateStatus("Saved to " + f.getName());
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Error saving: " + ex.getMessage());
            }
        }
    }

    private void onLoad() {
        JFileChooser fileChooser = new JFileChooser();
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File f = fileChooser.getSelectedFile();
            List<Contact> loaded = new ArrayList<>();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    Contact c = Contact.fromCSVLine(line);
                    if (c != null) loaded.add(c);
                }
                tableModel.clear();
                for (Contact c : loaded) tableModel.addContact(c);
                updateStatus("Loaded " + loaded.size() + " contacts from " + f.getName());
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Error loading: " + ex.getMessage());
            }
        }
    }

    /* --- Helpers --- */
    private void applyFilters() {
        String text = searchField.getText().trim().toLowerCase();
        boolean onlyFav = showFavoritesCheckbox.isSelected();
        RowFilter<ContactsTableModel, Integer> rf = new RowFilter<ContactsTableModel, Integer>() {
            public boolean include(Entry<? extends ContactsTableModel, ? extends Integer> entry) {
                Contact c = tableModel.getContactAt(entry.getIdentifier());
                boolean matchesSearch = text.isEmpty() ||
                        c.firstName.toLowerCase().contains(text) ||
                        c.lastName.toLowerCase().contains(text) ||
                        c.phone.toLowerCase().contains(text) ||
                        c.email.toLowerCase().contains(text) ||
                        c.tags.stream().anyMatch(t -> t.toLowerCase().contains(text));
                return matchesSearch && (!onlyFav || c.favorite);
            }
        };
        sorter.setRowFilter(rf);
        updateStatus();
    }

    private void updateStatus() {
        int total = tableModel.getRowCount();
        int visible = table.getRowCount();
        statusBar.setText("Total contacts: " + total + "    Visible: " + visible);
    }

    private void updateStatus(String msg) {
        updateStatus();
        statusBar.setText(msg + "    | Total: " + tableModel.getRowCount() + "    Visible: " + table.getRowCount());
    }
}

/* --- Custom dialog for Add/Edit --- */
class ContactDialog extends JDialog {
    private Contact result = null;

    private final JTextField firstNameField = new JTextField(20);
    private final JTextField lastNameField = new JTextField(20);
    private final JTextField phoneField = new JTextField(20);
    private final JTextField emailField = new JTextField(20);
    private final JRadioButton maleRb = new JRadioButton("Male");
    private final JRadioButton femaleRb = new JRadioButton("Female");
    private final JRadioButton otherRb = new JRadioButton("Other");
    private final JList<String> tagsList;

    private final JCheckBox favoriteCb = new JCheckBox("Favorite");

    ContactDialog(Frame owner, String title, Contact existing) {
        super(owner, title, true);
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        String[] allTags = {"me", "family", "friend", "work", "school", "gym", "other"};
        tagsList = new JList<>(allTags);
        tagsList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

        JPanel main = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6,6,6,6);
        gbc.anchor = GridBagConstraints.WEST;

        int row = 0;
        gbc.gridx = 0; gbc.gridy = row; main.add(new JLabel("First Name:"), gbc);
        gbc.gridx = 1; main.add(firstNameField, gbc); row++;

        gbc.gridx = 0; gbc.gridy = row; main.add(new JLabel("Last Name:"), gbc);
        gbc.gridx = 1; main.add(lastNameField, gbc); row++;

        gbc.gridx = 0; gbc.gridy = row; main.add(new JLabel("Phone:"), gbc);
        gbc.gridx = 1; main.add(phoneField, gbc); row++;

        gbc.gridx = 0; gbc.gridy = row; main.add(new JLabel("Email:"), gbc);
        gbc.gridx = 1; main.add(emailField, gbc); row++;

        gbc.gridx = 0; gbc.gridy = row; main.add(new JLabel("Gender:"), gbc);
        gbc.gridx = 1;
        JPanel genders = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        ButtonGroup bg = new ButtonGroup();
        bg.add(maleRb); bg.add(femaleRb); bg.add(otherRb);
        genders.add(maleRb); genders.add(femaleRb); genders.add(otherRb);
        main.add(genders, gbc); row++;

        gbc.gridx = 0; gbc.gridy = row; main.add(new JLabel("Tags:"), gbc);
        gbc.gridx = 1;
        JScrollPane tagsScroller = new JScrollPane(tagsList);
        tagsScroller.setPreferredSize(new Dimension(200, 80));
        main.add(tagsScroller, gbc); row++;

        gbc.gridx = 0; gbc.gridy = row; main.add(new JLabel("Favorite:"), gbc);
        gbc.gridx = 1; main.add(favoriteCb, gbc); row++;

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton ok = new JButton("OK");
        JButton cancel = new JButton("Cancel");
        btns.add(ok);
        btns.add(cancel);

        ok.addActionListener(e -> onOK());
        cancel.addActionListener(e -> {
            result = null;
            dispose();
        });

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(main, BorderLayout.CENTER);
        getContentPane().add(btns, BorderLayout.SOUTH);
        pack();
        setLocationRelativeTo(owner);

        if (existing != null) {
            firstNameField.setText(existing.firstName);
            lastNameField.setText(existing.lastName);
            phoneField.setText(existing.phone);
            emailField.setText(existing.email);
            favoriteCb.setSelected(existing.favorite);
            switch (existing.gender.toLowerCase()) {
                case "male": maleRb.setSelected(true); break;
                case "female": femaleRb.setSelected(true); break;
                default: otherRb.setSelected(true); break;
            }
            ListModel<String> lm = tagsList.getModel();
            List<Integer> sel = new ArrayList<>();
            for (int i=0;i<lm.getSize();i++) {
                if (existing.tags.contains(lm.getElementAt(i))) sel.add(Integer.valueOf(i));
            }
            int[] idx = sel.stream().mapToInt(Integer::intValue).toArray();
            tagsList.setSelectedIndices(idx);
        } else {
            otherRb.setSelected(true);
        }
    }

    private void onOK() {
        String fn = firstNameField.getText().trim();
        String ln = lastNameField.getText().trim();
        String ph = phoneField.getText().trim();
        String em = emailField.getText().trim();
        String gender = maleRb.isSelected() ? "Male" : femaleRb.isSelected() ? "Female" : "Other";
        List<String> tags = tagsList.getSelectedValuesList();

        if (ph.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Phone number is required.",
                    "Validation Error", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // phone validation
        if (!ph.matches("^\\+380\\d{9}$")) {
            JOptionPane.showMessageDialog(this,
                    "Invalid phone format. Use +380XXXXXXXXX (e.g. +380501234567)",
                    "Validation Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // email validation
        if (!em.isEmpty() && !em.matches("^[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,6}$")) {
            JOptionPane.showMessageDialog(this,
                    "Invalid email format. Example: example@gmail.com",
                    "Validation Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        result = new Contact(fn, ln, ph, em, gender, tags, favoriteCb.isSelected());
        dispose();
    }



    Contact getResult() { return result; }
}
