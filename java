import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.*;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import java.util.List;

/**
 * Attendance Management System (Java Swing)
 * Features: add/delete students, mark daily attendance, attendance report
 * with percentage, CSV persistence (students.csv, attendance.csv).
 *
 * Compile: javac AttendanceManagement.java
 * Run:     java AttendanceManagement
 */
public class AttendanceManagement extends JFrame {

    private static final Path STUDENTS_FILE = Paths.get("students.csv");
    private static final Path ATTENDANCE_FILE = Paths.get("attendance.csv");
    private static final double MIN_PERCENT = 75.0;

    // id -> name
    private final Map<String, String> students = new LinkedHashMap<>();
    // date -> (id -> present?)
    private final Map<String, Map<String, Boolean>> attendance = new TreeMap<>();

    // Students tab
    private DefaultTableModel studentModel;
    private JTextField idField, nameField;

    // Mark tab
    private DefaultTableModel markModel;
    private JTextField dateField;

    // Report tab
    private DefaultTableModel reportModel;

    public AttendanceManagement() {
        super("Attendance Management System");
        loadData();

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Students", buildStudentsPanel());
        tabs.addTab("Mark Attendance", buildMarkPanel());
        tabs.addTab("Report", buildReportPanel());
        tabs.addChangeListener(e -> {
            if (tabs.getSelectedIndex() == 1) loadMarkTable();
            if (tabs.getSelectedIndex() == 2) refreshReport();
        });

        add(tabs);
        setSize(720, 480);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        refreshStudentTable();
    }

    // ---------------------------------------------------------- Students tab
    private JPanel buildStudentsPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        studentModel = new DefaultTableModel(new String[]{"Student ID", "Name"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable table = new JTable(studentModel);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel form = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        idField = new JTextField(8);
        nameField = new JTextField(14);
        JButton addBtn = new JButton("Add Student");
        JButton delBtn = new JButton("Delete Selected");
        form.add(new JLabel("ID:"));
        form.add(idField);
        form.add(new JLabel("Name:"));
        form.add(nameField);
        form.add(addBtn);
        form.add(delBtn);
        panel.add(form, BorderLayout.SOUTH);

        addBtn.addActionListener(e -> addStudent());
        nameField.addActionListener(e -> addStudent());
        delBtn.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) {
                msg("Select a student first.");
                return;
            }
            String id = (String) studentModel.getValueAt(row, 0);
            int ok = JOptionPane.showConfirmDialog(this,
                    "Delete " + id + " and all their attendance records?",
                    "Confirm", JOptionPane.YES_NO_OPTION);
            if (ok == JOptionPane.YES_OPTION) {
                students.remove(id);
                for (Map<String, Boolean> day : attendance.values()) day.remove(id);
                attendance.values().removeIf(Map::isEmpty);
                saveData();
                refreshStudentTable();
            }
        });
        return panel;
    }

    private void addStudent() {
        String id = idField.getText().trim();
        String name = nameField.getText().trim().replace(",", " ");
        if (id.isEmpty() || name.isEmpty()) {
            msg("Enter both ID and name.");
            return;
        }
        id = id.replace(",", "");
        if (students.containsKey(id)) {
            msg("A student with this ID already exists.");
            return;
        }
        students.put(id, name);
        saveData();
        refreshStudentTable();
        idField.setText("");
        nameField.setText("");
        idField.requestFocus();
    }

    private void refreshStudentTable() {
        studentModel.setRowCount(0);
        students.forEach((id, name) -> studentModel.addRow(new Object[]{id, name}));
    }

    // ------------------------------------------------------ Mark attendance
    private JPanel buildMarkPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        markModel = new DefaultTableModel(new String[]{"Student ID", "Name", "Present"}, 0) {
            @Override public Class<?> getColumnClass(int c) {
                return c == 2 ? Boolean.class : String.class;
            }
            @Override public boolean isCellEditable(int r, int c) { return c == 2; }
        };
        panel.add(new JScrollPane(new JTable(markModel)), BorderLayout.CENTER);

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        dateField = new JTextField(LocalDate.now().toString(), 10);
        JButton loadBtn = new JButton("Load Date");
        JButton allPresent = new JButton("All Present");
        JButton allAbsent = new JButton("All Absent");
        top.add(new JLabel("Date (YYYY-MM-DD):"));
        top.add(dateField);
        top.add(loadBtn);
        top.add(allPresent);
        top.add(allAbsent);
        panel.add(top, BorderLayout.NORTH);

        JButton saveBtn = new JButton("Save Attendance");
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottom.add(saveBtn);
        panel.add(bottom, BorderLayout.SOUTH);

        loadBtn.addActionListener(e -> loadMarkTable());
        allPresent.addActionListener(e -> setAll(true));
        allAbsent.addActionListener(e -> setAll(false));
        saveBtn.addActionListener(e -> saveAttendanceForDate());
        return panel;
    }

    private String validDate() {
        try {
            return LocalDate.parse(dateField.getText().trim()).toString();
        } catch (Exception ex) {
            msg("Invalid date. Use format YYYY-MM-DD.");
            return null;
        }
    }

    private void loadMarkTable() {
        String date = validDate();
        if (date == null) return;
        Map<String, Boolean> day = attendance.getOrDefault(date, Collections.emptyMap());
        markModel.setRowCount(0);
        students.forEach((id, name) ->
                markModel.addRow(new Object[]{id, name, day.getOrDefault(id, true)}));
    }

    private void setAll(boolean value) {
        for (int i = 0; i < markModel.getRowCount(); i++) markModel.setValueAt(value, i, 2);
    }

    private void saveAttendanceForDate() {
        String date = validDate();
        if (date == null) return;
        if (markModel.getRowCount() == 0) {
            msg("No students to mark. Add students first.");
            return;
        }
        Map<String, Boolean> day = new LinkedHashMap<>();
        for (int i = 0; i < markModel.getRowCount(); i++) {
            day.put((String) markModel.getValueAt(i, 0), (Boolean) markModel.getValueAt(i, 2));
        }
        attendance.put(date, day);
        saveData();
        msg("Attendance saved for " + date + ".");
    }

    // --------------------------------------------------------------- Report
    private JPanel buildReportPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        reportModel = new DefaultTableModel(
                new String[]{"Student ID", "Name", "Present", "Total Days", "Attendance %"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable table = new JTable(reportModel) {
            @Override public Component prepareRenderer(javax.swing.table.TableCellRenderer r, int row, int col) {
                Component c = super.prepareRenderer(r, row, col);
                if (!isRowSelected(row)) {
                    double pct = Double.parseDouble(
                            reportModel.getValueAt(row, 4).toString().replace("%", ""));
                    boolean low = (int) reportModel.getValueAt(row, 3) > 0 && pct < MIN_PERCENT;
                    c.setBackground(low ? new Color(255, 205, 205) : Color.WHITE);
                }
                return c;
            }
        };
        panel.add(new JScrollPane(table), BorderLayout.CENTER);

        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(e -> refreshReport());
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bottom.add(refresh);
        bottom.add(new JLabel("Rows in red are below " + (int) MIN_PERCENT + "% attendance."));
        panel.add(bottom, BorderLayout.SOUTH);
        return panel;
    }

    private void refreshReport() {
        reportModel.setRowCount(0);
        for (Map.Entry<String, String> s : students.entrySet()) {
            int present = 0, total = 0;
            for (Map<String, Boolean> day : attendance.values()) {
                Boolean p = day.get(s.getKey());
                if (p != null) {
                    total++;
                    if (p) present++;
                }
            }
            double pct = total == 0 ? 0 : present * 100.0 / total;
            reportModel.addRow(new Object[]{s.getKey(), s.getValue(), present, total,
                    String.format("%.1f%%", pct)});
        }
    }

    // ---------------------------------------------------------- Persistence
    private void loadData() {
        try {
            if (Files.exists(STUDENTS_FILE)) {
                for (String line : Files.readAllLines(STUDENTS_FILE)) {
                    String[] p = line.split(",", 2);
                    if (p.length == 2) students.put(p[0], p[1]);
                }
            }
            if (Files.exists(ATTENDANCE_FILE)) {
                for (String line : Files.readAllLines(ATTENDANCE_FILE)) {
                    String[] p = line.split(",");
                    if (p.length == 3) {
                        attendance.computeIfAbsent(p[0], k -> new LinkedHashMap<>())
                                .put(p[1], p[2].equals("P"));
                    }
                }
            }
        } catch (IOException ex) {
            msg("Could not load data: " + ex.getMessage());
        }
    }

    private void saveData() {
        try {
            List<String> sl = new ArrayList<>();
            students.forEach((id, name) -> sl.add(id + "," + name));
            Files.write(STUDENTS_FILE, sl);

            List<String> al = new ArrayList<>();
            attendance.forEach((date, day) ->
                    day.forEach((id, p) -> al.add(date + "," + id + "," + (p ? "P" : "A"))));
            Files.write(ATTENDANCE_FILE, al);
        } catch (IOException ex) {
            msg("Could not save data: " + ex.getMessage());
        }
    }

    private void msg(String text) {
        JOptionPane.showMessageDialog(this, text);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new AttendanceManagement().setVisible(true));
    }
}
