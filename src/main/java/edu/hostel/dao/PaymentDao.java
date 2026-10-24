package edu.hostel.dao;

import edu.hostel.model.Payment;
import java.util.List;

public interface PaymentDao {
    List<Payment> findAll();
    void add(int studentId, double amount, String note);
    void remove(int id);
}
