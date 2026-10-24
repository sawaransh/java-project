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
            seedDemonstrationData(connection, statement);
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
}
