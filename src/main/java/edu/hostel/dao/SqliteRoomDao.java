package edu.hostel.dao;

import edu.hostel.db.Database;
import edu.hostel.exception.HostelException;
import edu.hostel.model.Room;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class SqliteRoomDao implements RoomDao {
    private final Database database;
    public SqliteRoomDao(Database database) { this.database = database; }

    @Override public List<Room> findAll() {
        List<Room> rooms = new ArrayList<>();
        String sql = "SELECT r.id,r.number,r.block,r.capacity,COUNT(a.id) AS occupied FROM rooms r LEFT JOIN allocations a ON a.room_id=r.id GROUP BY r.id ORDER BY r.block,r.number";
        try (var c = database.connect(); var ps = c.prepareStatement(sql); var rs = ps.executeQuery()) {
            while (rs.next()) rooms.add(new Room(rs.getInt("id"), rs.getString("number"), rs.getString("block"), rs.getInt("capacity"), rs.getInt("occupied")));
            return rooms;
        } catch (SQLException e) { throw new HostelException("Could not load rooms", e); }
    }

    @Override public void add(String number, String block, int capacity) {
        try (var c = database.connect(); var ps = c.prepareStatement("INSERT INTO rooms(number,block,capacity) VALUES(?,?,?)")) {
            ps.setString(1, number); ps.setString(2, block); ps.setInt(3, capacity); ps.executeUpdate();
        } catch (SQLException e) { throw new HostelException("Could not add room. The room may already exist in this block.", e); }
    }

    @Override public void allocate(int studentId, int roomId) {
        String occupiedSql = "SELECT COUNT(*) FROM allocations WHERE room_id=?";
        try (var c = database.connect()) {
            c.setAutoCommit(false);
            try (var check = c.prepareStatement("SELECT capacity FROM rooms WHERE id=?")) {
                check.setInt(1, roomId);
                try (var rs = check.executeQuery()) {
                    if (!rs.next()) throw new HostelException("Choose an existing room.");
                    int capacity = rs.getInt(1);
                    try (var count = c.prepareStatement(occupiedSql)) {
                        count.setInt(1, roomId);
                        try (var occupants = count.executeQuery()) {
                            occupants.next();
                            if (occupants.getInt(1) >= capacity) throw new HostelException("That room is full. Choose a room with space.");
                        }
                    }
                }
            }
            try (var insert = c.prepareStatement("INSERT INTO allocations(student_id,room_id,allocated_on) VALUES(?,?,?)")) {
                insert.setInt(1, studentId); insert.setInt(2, roomId); insert.setString(3, LocalDate.now().toString()); insert.executeUpdate();
            }
            c.commit();
        } catch (HostelException e) { throw e; }
        catch (SQLException e) { throw new HostelException("Could not allocate student. They may already have a room.", e); }
    }

    @Override public void deallocate(int studentId) {
        try (var c = database.connect(); var ps = c.prepareStatement("DELETE FROM allocations WHERE student_id=?")) {
            ps.setInt(1, studentId);
            if (ps.executeUpdate() == 0) throw new HostelException("That student has no room allocation.");
        } catch (HostelException e) { throw e; }
        catch (SQLException e) { throw new HostelException("Could not release the room allocation.", e); }
    }
}
