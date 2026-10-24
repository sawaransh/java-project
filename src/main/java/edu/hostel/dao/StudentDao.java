package edu.hostel.dao;

import edu.hostel.model.Student;
import java.util.List;

public interface StudentDao {
    List<Student> findAll();
    void add(String name, String phone, String email);
    void update(int id, String name, String phone, String email);
    void remove(int id);
}
