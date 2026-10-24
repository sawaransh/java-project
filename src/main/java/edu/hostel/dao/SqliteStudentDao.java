package edu.hostel.dao;

import edu.hostel.db.Database;
import edu.hostel.exception.HostelException;
import edu.hostel.model.Student;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class SqliteStudentDao implements StudentDao {
    private final Database database;
    public SqliteStudentDao(Database database) { this.database = database; }

    @Override public List<Student> findAll() {
        List<Student> students = new ArrayList<>();
        try (var c = database.connect(); var ps = c.prepareStatement("SELECT id,name,phone,email FROM students ORDER BY name"); var rs = ps.executeQuery()) {
            while (rs.next()) students.add(new Student(rs.getInt("id"), rs.getString("name"), rs.getString("phone"), rs.getString("email")));
            return students;
        } catch (SQLException e) { throw new HostelException("Could not load students", e); }
    }

    @Override public void add(String name, String phone, String email) {
        try (var c = database.connect(); var ps = c.prepareStatement("INSERT INTO students(name,phone,email) VALUES(?,?,?)")) {
            ps.setString(1, name); ps.setString(2, phone); ps.setString(3, email); ps.executeUpdate();
        } catch (SQLException e) { throw new HostelException("Could not add student. Check that the email is unique.", e); }
    }

    @Override public void update(int id, String name, String phone, String email) {
        try (var c = database.connect(); var ps = c.prepareStatement("UPDATE students SET name=?,phone=?,email=? WHERE id=?")) {
            ps.setString(1, name); ps.setString(2, phone); ps.setString(3, email); ps.setInt(4, id);
            if (ps.executeUpdate() == 0) throw new HostelException("Student no longer exists.");
        } catch (HostelException e) { throw e; }
        catch (SQLException e) { throw new HostelException("Could not update student. Check that the email is unique.", e); }
    }

    @Override public void remove(int id) {
        try (var c = database.connect(); var ps = c.prepareStatement("DELETE FROM students WHERE id=?")) {
            ps.setInt(1, id);
            if (ps.executeUpdate() == 0) throw new HostelException("Select a student first.");
        } catch (HostelException e) { throw e; }
        catch (SQLException e) { throw new HostelException("Remove this student's room allocation and payments before deleting the record.", e); }
    }
}
