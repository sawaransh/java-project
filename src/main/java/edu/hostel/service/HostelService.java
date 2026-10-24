package edu.hostel.service;

import edu.hostel.dao.PaymentDao;
import edu.hostel.dao.RoomDao;
import edu.hostel.dao.StudentDao;
import edu.hostel.exception.HostelException;
import edu.hostel.model.Payment;
import edu.hostel.model.Room;
import edu.hostel.model.Student;
import java.util.List;

public class HostelService {
    private final StudentDao students;
    private final RoomDao rooms;
    private final PaymentDao payments;

    public HostelService(StudentDao students, RoomDao rooms, PaymentDao payments) {
        this.students = students; this.rooms = rooms; this.payments = payments;
    }
    public List<Student> students() { return students.findAll(); }
    public List<Room> rooms() { return rooms.findAll(); }
    public List<Payment> payments() { return payments.findAll(); }

    public void addStudent(String name, String phone, String email) {
        validateStudent(name, phone, email);
        students.add(name.trim(), phone.trim(), email.trim().toLowerCase());
    }
    public void updateStudent(int id, String name, String phone, String email) {
        validateStudent(name, phone, email); students.update(id, name.trim(), phone.trim(), email.trim().toLowerCase());
    }
    public void removeStudent(int id) { students.remove(id); }
    private void validateStudent(String name, String phone, String email) {
        require(name, "Enter the student's name."); require(phone, "Enter a phone number."); require(email, "Enter an email address.");
        if (!email.contains("@")) throw new HostelException("Enter a valid email address.");
    }
    public void addRoom(String number, String block, int capacity) {
        require(number, "Enter a room number."); require(block, "Enter a block.");
        if (capacity < 1 || capacity > 20) throw new HostelException("Room capacity must be between 1 and 20.");
        rooms.add(number.trim(), block.trim(), capacity);
    }
    public void allocate(int studentId, int roomId) { rooms.allocate(studentId, roomId); }
    public void deallocate(int studentId) { rooms.deallocate(studentId); }
    public void addPayment(int studentId, double amount, String note) {
        if (studentId < 1) throw new HostelException("Choose a student first.");
        if (!Double.isFinite(amount) || amount <= 0) throw new HostelException("Payment must be greater than zero.");
        payments.add(studentId, amount, note == null ? "" : note.trim());
    }
    public void removePayment(int id) { payments.remove(id); }
    private void require(String value, String message) { if (value == null || value.isBlank()) throw new HostelException(message); }
}
