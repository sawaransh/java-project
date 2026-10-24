package edu.hostel.model;

public class Room {
    private final int id;
    private final String number;
    private final String block;
    private final int capacity;
    private final int occupied;

    public Room(int id, String number, String block, int capacity, int occupied) {
        this.id = id; this.number = number; this.block = block; this.capacity = capacity; this.occupied = occupied;
    }
    public int getId() { return id; }
    public String getNumber() { return number; }
    public String getBlock() { return block; }
    public int getCapacity() { return capacity; }
    public int getOccupied() { return occupied; }
    @Override public String toString() { return block + " / " + number + " (" + occupied + "/" + capacity + ")"; }
}
