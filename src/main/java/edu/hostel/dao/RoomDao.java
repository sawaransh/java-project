package edu.hostel.dao;

import edu.hostel.model.Room;
import java.util.List;

public interface RoomDao {
    List<Room> findAll();
    void add(String number, String block, int capacity);
    void allocate(int studentId, int roomId);
    void deallocate(int studentId);
}
