package edu.hostel.service;

import edu.hostel.db.Database;
import edu.hostel.exception.HostelException;
import edu.hostel.model.UserSession;
import java.sql.SQLException;

public class AuthService {
    private final Database database;
    public AuthService(Database database) { this.database = database; }

    public UserSession login(String email, String password) {
        String sql = "SELECT a.id,a.email,a.password_hash,a.salt,a.role,a.student_id,COALESCE(s.name,'Hostel administrator') AS display_name " +
                "FROM accounts a LEFT JOIN students s ON s.id=a.student_id WHERE lower(a.email)=lower(?)";
        try (var connection = database.connect(); var query = connection.prepareStatement(sql)) {
            query.setString(1, email == null ? "" : email.trim());
            try (var result = query.executeQuery()) {
                if (!result.next() || password == null || !PasswordUtil.matches(password, result.getString("salt"), result.getString("password_hash"))) {
                    throw new HostelException("Email or password is incorrect.");
                }
                int id = result.getInt("student_id"); Integer studentId = result.wasNull() ? null : id;
                return new UserSession(result.getInt("id"), result.getString("email"), result.getString("role"), studentId, result.getString("display_name"));
            }
        } catch (HostelException e) { throw e; }
        catch (SQLException e) { throw new HostelException("Unable to sign in right now.", e); }
    }

    public UserSession registerStudent(String name, String email, String phone, String password) {
        if (name == null || name.isBlank() || email == null || !email.contains("@") || phone == null || phone.isBlank()) {
            throw new HostelException("Enter your name, a valid email, and a phone number.");
        }
        if (password == null || password.length() < 8) throw new HostelException("Choose a password with at least 8 characters.");
        String normalizedEmail = email.trim().toLowerCase(); String salt = PasswordUtil.newSalt();
        try (var connection = database.connect()) {
            connection.setAutoCommit(false);
            try (var student = connection.prepareStatement("INSERT INTO students(name,phone,email) VALUES(?,?,?)", java.sql.Statement.RETURN_GENERATED_KEYS)) {
                student.setString(1, name.trim()); student.setString(2, phone.trim()); student.setString(3, normalizedEmail); student.executeUpdate();
                int studentId;
                try (var keys = student.getGeneratedKeys()) { if (!keys.next()) throw new SQLException("Student record was not created"); studentId = keys.getInt(1); }
                try (var account = connection.prepareStatement("INSERT INTO accounts(email,password_hash,salt,role,student_id) VALUES(?,?,?,'STUDENT',?)")) {
                    account.setString(1, normalizedEmail); account.setString(2, PasswordUtil.hash(password, salt)); account.setString(3, salt); account.setInt(4, studentId); account.executeUpdate();
                }
                connection.commit();
                return login(normalizedEmail, password);
            } catch (SQLException | RuntimeException e) { connection.rollback(); throw e; }
            finally { connection.setAutoCommit(true); }
        } catch (HostelException e) { throw e; }
        catch (SQLException e) { throw new HostelException("Could not create account. That email may already be registered.", e); }
    }
}
