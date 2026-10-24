package edu.hostel.model;

public record ServiceRequest(int id, String studentName, String subject, String details,
                            String room, String createdAt, String status, String type) {}
