package edu.hostel.model;

public record ResidentRecord(int studentId, String name, String email, String phone,
                             String room, String allocatedOn) {}
