package edu.hostel.model;

public record UserSession(int accountId, String email, String role, Integer studentId, String displayName) {
    public boolean isAdmin() { return "ADMIN".equals(role); }
}
