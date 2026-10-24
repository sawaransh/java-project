package edu.hostel.dao;

import edu.hostel.db.Database;
import edu.hostel.exception.HostelException;
import edu.hostel.model.Payment;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class SqlitePaymentDao implements PaymentDao {
    private final Database database;
    public SqlitePaymentDao(Database database) { this.database = database; }

    @Override public List<Payment> findAll() {
        List<Payment> payments = new ArrayList<>();
        String sql = "SELECT p.id,s.name,p.amount,p.paid_on,p.note FROM payments p JOIN students s ON s.id=p.student_id ORDER BY p.paid_on DESC,p.id DESC";
        try (var c = database.connect(); var ps = c.prepareStatement(sql); var rs = ps.executeQuery()) {
            while (rs.next()) payments.add(new Payment(rs.getInt("id"), rs.getString("name"), rs.getDouble("amount"), LocalDate.parse(rs.getString("paid_on")), rs.getString("note")));
            return payments;
        } catch (SQLException e) { throw new HostelException("Could not load payments", e); }
    }

    @Override public void add(int studentId, double amount, String note) {
        try (var c = database.connect(); var ps = c.prepareStatement("INSERT INTO payments(student_id,amount,paid_on,note) VALUES(?,?,?,?)")) {
            ps.setInt(1, studentId); ps.setDouble(2, amount); ps.setString(3, LocalDate.now().toString()); ps.setString(4, note); ps.executeUpdate();
        } catch (SQLException e) { throw new HostelException("Could not record payment", e); }
    }

    @Override public void remove(int id) {
        try (var c = database.connect(); var ps = c.prepareStatement("DELETE FROM payments WHERE id=?")) {
            ps.setInt(1, id); if (ps.executeUpdate() == 0) throw new HostelException("Select a payment row first.");
        } catch (HostelException e) { throw e; }
        catch (SQLException e) { throw new HostelException("Could not remove payment", e); }
    }
}
