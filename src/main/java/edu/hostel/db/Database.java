package edu.hostel.db;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import edu.hostel.service.PasswordUtil;

public class Database {
    private final String url;

    public Database() throws Exception {
        Properties properties = new Properties();
        try (InputStream input = Database.class.getResourceAsStream("/app.properties")) {
            if (input != null) properties.load(input);
        }
        String file = properties.getProperty("database.path", "data/hostel.db");
        Path path = Path.of(file);
        if (path.getParent() != null) Files.createDirectories(path.getParent());
        this.url = "jdbc:sqlite:" + path.toAbsolutePath();
        initialize();
    }

    public Connection connect() throws SQLException {
        Connection connection = DriverManager.getConnection(url);
        try (Statement statement = connection.createStatement()) { statement.execute("PRAGMA foreign_keys = ON"); }
        return connection;
    }

    private void initialize() throws SQLException {
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS students (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, phone TEXT NOT NULL, email TEXT NOT NULL UNIQUE)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS rooms (id INTEGER PRIMARY KEY AUTOINCREMENT, number TEXT NOT NULL, block TEXT NOT NULL, capacity INTEGER NOT NULL CHECK(capacity > 0), UNIQUE(number, block))");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS allocations (id INTEGER PRIMARY KEY AUTOINCREMENT, student_id INTEGER NOT NULL UNIQUE REFERENCES students(id), room_id INTEGER NOT NULL REFERENCES rooms(id), allocated_on TEXT NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS payments (id INTEGER PRIMARY KEY AUTOINCREMENT, student_id INTEGER NOT NULL REFERENCES students(id), amount REAL NOT NULL CHECK(amount > 0), paid_on TEXT NOT NULL, note TEXT NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS app_metadata (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS accounts (id INTEGER PRIMARY KEY AUTOINCREMENT, email TEXT NOT NULL UNIQUE, password_hash TEXT NOT NULL, salt TEXT NOT NULL, role TEXT NOT NULL CHECK(role IN ('ADMIN','STUDENT')), student_id INTEGER UNIQUE REFERENCES students(id) ON DELETE CASCADE)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS applications (id INTEGER PRIMARY KEY AUTOINCREMENT, student_id INTEGER NOT NULL UNIQUE REFERENCES students(id) ON DELETE CASCADE, preferred_block TEXT NOT NULL, notes TEXT NOT NULL, status TEXT NOT NULL DEFAULT 'PENDING', submitted_at TEXT NOT NULL, reviewed_at TEXT)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS resources (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL UNIQUE, category TEXT NOT NULL, location TEXT NOT NULL, quantity INTEGER NOT NULL CHECK(quantity > 0))");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS resource_requests (id INTEGER PRIMARY KEY AUTOINCREMENT, student_id INTEGER NOT NULL REFERENCES students(id) ON DELETE CASCADE, resource_id INTEGER NOT NULL REFERENCES resources(id), requested_at TEXT NOT NULL, status TEXT NOT NULL DEFAULT 'PENDING', note TEXT NOT NULL DEFAULT '')");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS maintenance_requests (id INTEGER PRIMARY KEY AUTOINCREMENT, student_id INTEGER NOT NULL REFERENCES students(id) ON DELETE CASCADE, room_id INTEGER REFERENCES rooms(id), category TEXT NOT NULL, details TEXT NOT NULL, status TEXT NOT NULL DEFAULT 'OPEN', admin_note TEXT NOT NULL DEFAULT '', reported_at TEXT NOT NULL)");
            seedDemonstrationData(connection, statement);
            seedDemoAccountsAndWorkflows(connection, statement);
        }
    }

    private void seedDemonstrationData(Connection connection, Statement statement) throws SQLException {
        try (var result = statement.executeQuery("SELECT value FROM app_metadata WHERE key='demo_data_seeded'")) {
            if (result.next() || count(statement, "students") > 0) return;
        }

        connection.setAutoCommit(false);
        try {
            if (count(statement, "rooms") == 0) {
                statement.executeUpdate("INSERT INTO rooms(number, block, capacity) VALUES ('101','A',2),('102','A',3),('201','B',2)");
            }
            try (PreparedStatement addRoom = connection.prepareStatement(
                    "INSERT OR IGNORE INTO rooms(number,block,capacity) VALUES(?,?,4)")) {
                for (int i = 1; count(statement, "rooms") < 30 && i <= 40; i++) {
                    addRoom.setString(1, String.format("D-%03d", i));
                    addRoom.setString(2, "Demo");
                    addRoom.executeUpdate();
                }
            }

            String[] firstNames = {"Aarav", "Aanya", "Aditya", "Anaya", "Arjun", "Diya", "Ishaan", "Isha", "Kabir", "Kavya", "Krish", "Meera", "Neel", "Nisha", "Pranav", "Riya", "Rohan", "Saanvi", "Vivaan", "Zoya"};
            String[] lastNames = {"Sharma", "Verma", "Gupta", "Mehta", "Rao", "Kapoor"};
            List<Integer> studentIds = new ArrayList<>();
            try (PreparedStatement addStudent = connection.prepareStatement(
                    "INSERT INTO students(name,phone,email) VALUES(?,?,?)")) {
                for (int i = 0; i < 120; i++) {
                    String name = firstNames[i % firstNames.length] + " " + lastNames[i / firstNames.length];
                    addStudent.setString(1, name);
                    addStudent.setString(2, String.format("+91-90000-%05d", 10000 + i));
                    addStudent.setString(3, String.format("student%03d@demo.hostel", i + 1));
                    addStudent.addBatch();
                }
                addStudent.executeBatch();
            }
            try (PreparedStatement findStudents = connection.prepareStatement("SELECT id FROM students WHERE email LIKE 'student%@demo.hostel' ORDER BY id");
                 var results = findStudents.executeQuery()) {
                while (results.next()) studentIds.add(results.getInt(1));
            }

            List<int[]> rooms = new ArrayList<>();
            try (var results = statement.executeQuery("SELECT id,capacity FROM rooms ORDER BY id")) {
                while (results.next()) rooms.add(new int[]{results.getInt(1), results.getInt(2)});
            }
            try (PreparedStatement allocate = connection.prepareStatement(
                    "INSERT INTO allocations(student_id,room_id,allocated_on) VALUES(?,?,?)")) {
                int studentIndex = 0;
                for (int[] room : rooms) {
                    for (int bed = 0; bed < room[1] && studentIndex < 96; bed++, studentIndex++) {
                        allocate.setInt(1, studentIds.get(studentIndex));
                        allocate.setInt(2, room[0]);
                        allocate.setString(3, LocalDate.now().minusDays(studentIndex % 180).toString());
                        allocate.addBatch();
                    }
                }
                allocate.executeBatch();
            }

            try (PreparedStatement addPayment = connection.prepareStatement(
                    "INSERT INTO payments(student_id,amount,paid_on,note) VALUES(?,?,?,?)")) {
                for (int i = 0; i < 90; i++) {
                    int installments = i % 3 == 0 ? 1 : 2;
                    for (int part = 1; part <= installments; part++) {
                        addPayment.setInt(1, studentIds.get(i));
                        addPayment.setDouble(2, (part == 1 ? 8500 : 5000) + (i % 5) * 250);
                        addPayment.setString(3, LocalDate.now().minusDays((i * 2L + part * 11L) % 180).toString());
                        addPayment.setString(4, "Term fee - installment " + part);
                        addPayment.addBatch();
                    }
                }
                addPayment.executeBatch();
            }
            statement.executeUpdate("INSERT INTO app_metadata(key,value) VALUES('demo_data_seeded','120 students')");
            connection.commit();
        } catch (SQLException | RuntimeException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    private int count(Statement statement, String table) throws SQLException {
        try (var result = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            return result.next() ? result.getInt(1) : 0;
        }
    }

    private void seedDemoAccountsAndWorkflows(Connection connection, Statement statement) throws SQLException {
        String adminExists = "SELECT COUNT(*) FROM accounts WHERE email='admin@hostel.demo'";
        boolean hasAdmin;
        try (var result = statement.executeQuery(adminExists)) {
            hasAdmin = result.next() && result.getInt(1) > 0;
        }
        if (!hasAdmin) addAccount(connection, "admin@hostel.demo", "Admin123!", "ADMIN", null);

        List<Object[]> demoStudents = new ArrayList<>();
        try (var students = statement.executeQuery("SELECT id,email FROM students WHERE email LIKE 'student%@demo.hostel' ORDER BY id")) {
            while (students.next()) demoStudents.add(new Object[]{students.getInt("id"), students.getString("email")});
        }
        for (Object[] row : demoStudents) {
            int studentId = (Integer) row[0]; String email = (String) row[1];
            try (var check = connection.prepareStatement("SELECT 1 FROM accounts WHERE email=?")) {
                check.setString(1, email);
                try (var exists = check.executeQuery()) {
                    if (!exists.next()) addAccount(connection, email, "Student123!", "STUDENT", studentId);
                }
            }
        }

        if (count(statement, "resources") == 0) {
            String[][] seed = {
                    {"Laundry washer", "Laundry", "Utility room", "6"}, {"Laundry dryer", "Laundry", "Utility room", "4"},
                    {"Study room slot", "Study", "Block A", "12"}, {"Sports kit", "Sports", "Recreation desk", "10"},
                    {"Indoor court slot", "Sports", "Activity hall", "8"}, {"Visitor parking", "Facility", "Gate 1", "20"},
                    {"Water dispenser", "Facility", "Every floor", "24"}, {"Projector", "Equipment", "Study room", "3"}
            };
            try (var add = connection.prepareStatement("INSERT INTO resources(name,category,location,quantity) VALUES(?,?,?,?)")) {
                for (String[] row : seed) {
                    add.setString(1, row[0]); add.setString(2, row[1]); add.setString(3, row[2]); add.setInt(4, Integer.parseInt(row[3])); add.addBatch();
                }
                add.executeBatch();
            }
        }

        if (count(statement, "applications") == 0) {
            List<Integer> unallocated = new ArrayList<>();
            try (var students = statement.executeQuery("SELECT s.id FROM students s LEFT JOIN allocations a ON a.student_id=s.id WHERE a.id IS NULL ORDER BY s.id")) {
                while (students.next()) unallocated.add(students.getInt(1));
            }
            try (var add = connection.prepareStatement("INSERT OR IGNORE INTO applications(student_id,preferred_block,notes,status,submitted_at) VALUES(?,?,?,?,?)")) {
                int index = 0;
                for (int studentId : unallocated) {
                    add.setInt(1, studentId); add.setString(2, index % 2 == 0 ? "A" : "B");
                    add.setString(3, "Demo application - preference " + (index % 4 + 1));
                    add.setString(4, index % 4 == 3 ? "WAITLISTED" : "PENDING");
                    add.setString(5, LocalDate.now().minusDays(index % 30).toString()); add.addBatch(); index++;
                }
                add.executeBatch();
            }
        }

        if (count(statement, "resource_requests") == 0) {
            List<Integer> demoStudentIds = new ArrayList<>();
            try (var students = statement.executeQuery("SELECT id FROM students ORDER BY id LIMIT 12")) { while (students.next()) demoStudentIds.add(students.getInt(1)); }
            try (var add = connection.prepareStatement("INSERT INTO resource_requests(student_id,resource_id,requested_at,status,note) VALUES(?,?,?,?,?)")) {
                int index = 0;
                for (int studentId : demoStudentIds) {
                    add.setInt(1, studentId); add.setInt(2, index % 8 + 1);
                    add.setString(3, LocalDate.now().minusDays(index).toString()); add.setString(4, index % 3 == 0 ? "PENDING" : "APPROVED");
                    add.setString(5, "Demo resource request"); add.addBatch(); index++;
                }
                add.executeBatch();
            }
        }

        if (count(statement, "maintenance_requests") == 0) {
            List<int[]> allocatedStudents = new ArrayList<>();
            try (var students = statement.executeQuery("SELECT s.id,a.room_id FROM students s JOIN allocations a ON a.student_id=s.id ORDER BY s.id LIMIT 8")) {
                while (students.next()) allocatedStudents.add(new int[]{students.getInt(1), students.getInt(2)});
            }
            try (var add = connection.prepareStatement("INSERT INTO maintenance_requests(student_id,room_id,category,details,status,reported_at) VALUES(?,?,?,?,?,?)")) {
                int index = 0;
                for (int[] row : allocatedStudents) {
                    add.setInt(1, row[0]); add.setInt(2, row[1]);
                    add.setString(3, index % 2 == 0 ? "Plumbing" : "Electrical"); add.setString(4, "Demo maintenance request " + (index + 1));
                    add.setString(5, index == 0 ? "IN_PROGRESS" : "OPEN"); add.setString(6, LocalDate.now().minusDays(index + 1).toString()); add.addBatch(); index++;
                }
                add.executeBatch();
            }
        }
    }

    private void addAccount(Connection connection, String email, String password, String role, Integer studentId) throws SQLException {
        String salt = PasswordUtil.newSalt(); String hash = PasswordUtil.hash(password, salt);
        try (var add = connection.prepareStatement("INSERT OR IGNORE INTO accounts(email,password_hash,salt,role,student_id) VALUES(?,?,?,?,?)")) {
            add.setString(1, email); add.setString(2, hash); add.setString(3, salt); add.setString(4, role);
            if (studentId == null) add.setNull(5, java.sql.Types.INTEGER); else add.setInt(5, studentId);
            add.executeUpdate();
        }
    }
}
