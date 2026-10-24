package edu.hostel.model;

import java.time.LocalDate;

public class Payment {
    private final int id;
    private final String studentName;
    private final double amount;
    private final LocalDate paidOn;
    private final String note;

    public Payment(int id, String studentName, double amount, LocalDate paidOn, String note) {
        this.id = id; this.studentName = studentName; this.amount = amount; this.paidOn = paidOn; this.note = note;
    }
    public int getId() { return id; }
    public String getStudentName() { return studentName; }
    public double getAmount() { return amount; }
    public LocalDate getPaidOn() { return paidOn; }
    public String getNote() { return note; }
}
