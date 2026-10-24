package edu.hostel.model;

public record ResourceItem(int id, String name, String category, String location, int quantity, int available) {
    @Override public String toString() { return name + " · " + available + " available"; }
}
