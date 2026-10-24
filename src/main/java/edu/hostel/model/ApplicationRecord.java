package edu.hostel.model;

public record ApplicationRecord(int id, int studentId, String studentName, String email,
                               String preferredBlock, String submittedAt, String status, String notes) {
    @Override public String toString() { return studentName + " · " + preferredBlock + " · " + status; }
}
