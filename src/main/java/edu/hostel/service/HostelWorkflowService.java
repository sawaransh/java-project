package edu.hostel.service;

import edu.hostel.db.Database;
import edu.hostel.exception.HostelException;
import edu.hostel.model.ApplicationRecord;
import edu.hostel.model.Payment;
import edu.hostel.model.ResidentRecord;
import edu.hostel.model.ResourceItem;
import edu.hostel.model.Room;
import edu.hostel.model.ServiceRequest;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class HostelWorkflowService {
    private final Database database;
    public HostelWorkflowService(Database database) { this.database = database; }

    public List<ApplicationRecord> applications() {
        String sql = "SELECT a.id,s.id AS student_id,s.name,s.email,a.preferred_block,a.submitted_at,a.status,a.notes FROM applications a JOIN students s ON s.id=a.student_id ORDER BY CASE a.status WHEN 'PENDING' THEN 0 WHEN 'WAITLISTED' THEN 1 ELSE 2 END,a.submitted_at";
        List<ApplicationRecord> rows = new ArrayList<>();
        try (var c=database.connect(); var q=c.prepareStatement(sql); var rs=q.executeQuery()) {
            while(rs.next()) rows.add(new ApplicationRecord(rs.getInt("id"),rs.getInt("student_id"),rs.getString("name"),rs.getString("email"),rs.getString("preferred_block"),rs.getString("submitted_at"),rs.getString("status"),rs.getString("notes")));
            return rows;
        } catch(SQLException e) { throw new HostelException("Could not load applications",e); }
    }

    public ApplicationRecord myApplication(int studentId) {
        return applications().stream().filter(a -> a.studentId()==studentId).findFirst().orElse(null);
    }

    public void submitApplication(int studentId, String preferredBlock, String notes) {
        if (preferredBlock == null || preferredBlock.isBlank()) throw new HostelException("Choose your preferred hostel block.");
        try (var c=database.connect(); var check=c.prepareStatement("SELECT 1 FROM allocations WHERE student_id=?")) {
            check.setInt(1,studentId);
            try (var rs=check.executeQuery()) { if(rs.next()) throw new HostelException("You already have a room. Contact the hostel office to request a transfer."); }
            String sql="INSERT INTO applications(student_id,preferred_block,notes,status,submitted_at,reviewed_at) VALUES(?,?,?,'PENDING',?,NULL) ON CONFLICT(student_id) DO UPDATE SET preferred_block=excluded.preferred_block,notes=excluded.notes,status='PENDING',submitted_at=excluded.submitted_at,reviewed_at=NULL";
            try(var q=c.prepareStatement(sql)){q.setInt(1,studentId);q.setString(2,preferredBlock.trim());q.setString(3,notes==null?"":notes.trim());q.setString(4,LocalDate.now().toString());q.executeUpdate();}
        } catch(HostelException e){throw e;} catch(SQLException e){throw new HostelException("Could not submit application",e);}
    }

    public void setApplicationStatus(int applicationId, String status) {
        if (!List.of("WAITLISTED","REJECTED").contains(status)) throw new HostelException("Choose a supported review status.");
        try(var c=database.connect();var q=c.prepareStatement("UPDATE applications SET status=?,reviewed_at=? WHERE id=? AND status IN ('PENDING','WAITLISTED')")){
            q.setString(1,status);q.setString(2,LocalDate.now().toString());q.setInt(3,applicationId);
            if(q.executeUpdate()==0)throw new HostelException("This application has already been finalized.");
        }catch(HostelException e){throw e;}catch(SQLException e){throw new HostelException("Could not update application",e);}
    }

    public void allotApplication(int applicationId, int roomId) {
        try(var c=database.connect()){
            c.setAutoCommit(false);
            try(var app=c.prepareStatement("SELECT student_id,status FROM applications WHERE id=?")){
                app.setInt(1,applicationId);
                try(var rs=app.executeQuery()){
                    if(!rs.next())throw new HostelException("Select a valid application.");
                    int studentId=rs.getInt("student_id"); String status=rs.getString("status");
                    if(!List.of("PENDING","WAITLISTED").contains(status))throw new HostelException("This application has already been finalized.");
                    try(var check=c.prepareStatement("SELECT r.capacity,COUNT(a.id) AS occupied FROM rooms r LEFT JOIN allocations a ON a.room_id=r.id WHERE r.id=? GROUP BY r.id")){
                        check.setInt(1,roomId);
                        try(var room=check.executeQuery()){
                            if(!room.next())throw new HostelException("Choose an existing room.");
                            if(room.getInt("occupied")>=room.getInt("capacity"))throw new HostelException("That room is full. Choose another room.");
                        }
                    }
                    try(var insert=c.prepareStatement("INSERT INTO allocations(student_id,room_id,allocated_on) VALUES(?,?,?)")){
                        insert.setInt(1,studentId);insert.setInt(2,roomId);insert.setString(3,LocalDate.now().toString());insert.executeUpdate();
                    }
                    try(var update=c.prepareStatement("UPDATE applications SET status='APPROVED',reviewed_at=? WHERE id=?")){
                        update.setString(1,LocalDate.now().toString());update.setInt(2,applicationId);update.executeUpdate();
                    }
                }
            }
            c.commit();
        }catch(HostelException e){throw e;}catch(SQLException e){throw new HostelException("Allotment could not be completed. The student may already have a room.",e);}
    }

    public List<Room> availableRooms() {
        List<Room> rows=new ArrayList<>();String sql="SELECT r.id,r.number,r.block,r.capacity,COUNT(a.id) AS occupied FROM rooms r LEFT JOIN allocations a ON a.room_id=r.id GROUP BY r.id HAVING occupied<r.capacity ORDER BY r.block,r.number";
        try(var c=database.connect();var q=c.prepareStatement(sql);var rs=q.executeQuery()){
            while(rs.next())rows.add(new Room(rs.getInt("id"),rs.getString("number"),rs.getString("block"),rs.getInt("capacity"),rs.getInt("occupied")));
            return rows;
        }catch(SQLException e){throw new HostelException("Could not load available rooms",e);}
    }

    public List<ResidentRecord> residents() {
        List<ResidentRecord> rows=new ArrayList<>();String sql="SELECT s.id,s.name,s.email,s.phone,COALESCE(r.block||' / '||r.number,'Not allotted') AS room,COALESCE(a.allocated_on,'') AS allocated_on FROM students s LEFT JOIN allocations a ON a.student_id=s.id LEFT JOIN rooms r ON r.id=a.room_id ORDER BY s.name";
        try(var c=database.connect();var q=c.prepareStatement(sql);var rs=q.executeQuery()){
            while(rs.next())rows.add(new ResidentRecord(rs.getInt("id"),rs.getString("name"),rs.getString("email"),rs.getString("phone"),rs.getString("room"),rs.getString("allocated_on")));
            return rows;
        }catch(SQLException e){throw new HostelException("Could not load residents",e);}
    }

    public String myRoom(int studentId) {
        String sql="SELECT r.block,r.number FROM allocations a JOIN rooms r ON r.id=a.room_id WHERE a.student_id=?";
        try(var c=database.connect();var q=c.prepareStatement(sql)){q.setInt(1,studentId);try(var rs=q.executeQuery()){return rs.next()?"Block "+rs.getString(1)+", Room "+rs.getString(2):"No room allotted yet";}}
        catch(SQLException e){throw new HostelException("Could not load room allotment",e);}
    }

    public List<ResourceItem> resources() {
        String sql="SELECT r.id,r.name,r.category,r.location,r.quantity,r.quantity-COUNT(CASE WHEN q.status='APPROVED' THEN 1 END) AS available FROM resources r LEFT JOIN resource_requests q ON q.resource_id=r.id GROUP BY r.id ORDER BY r.category,r.name";
        List<ResourceItem> rows=new ArrayList<>();try(var c=database.connect();var q=c.prepareStatement(sql);var rs=q.executeQuery()){
            while(rs.next())rows.add(new ResourceItem(rs.getInt("id"),rs.getString("name"),rs.getString("category"),rs.getString("location"),rs.getInt("quantity"),Math.max(0,rs.getInt("available"))));return rows;
        }catch(SQLException e){throw new HostelException("Could not load shared resources",e);}
    }

    public void addResource(String name,String category,String location,int quantity){
        if(name==null||name.isBlank()||category==null||category.isBlank()||location==null||location.isBlank()||quantity<1)throw new HostelException("Enter a name, category, location, and positive quantity.");
        try(var c=database.connect();var q=c.prepareStatement("INSERT INTO resources(name,category,location,quantity) VALUES(?,?,?,?)")){q.setString(1,name.trim());q.setString(2,category.trim());q.setString(3,location.trim());q.setInt(4,quantity);q.executeUpdate();}
        catch(SQLException e){throw new HostelException("Could not add resource. The resource name may already exist.",e);}
    }

    public void requestResource(int studentId,int resourceId,String note){
        try(var c=database.connect();var q=c.prepareStatement("INSERT INTO resource_requests(student_id,resource_id,requested_at,status,note) VALUES(?,?,?,'PENDING',?)")){
            q.setInt(1,studentId);q.setInt(2,resourceId);q.setString(3,LocalDate.now().toString());q.setString(4,note==null?"":note.trim());q.executeUpdate();
        }catch(SQLException e){throw new HostelException("Could not request this resource",e);}
    }

    public List<ServiceRequest> resourceRequests(Integer studentId){
        List<ServiceRequest> rows=new ArrayList<>();String sql="SELECT q.id,s.name,r.name AS subject,q.note AS details,'' AS room,q.requested_at,q.status FROM resource_requests q JOIN students s ON s.id=q.student_id JOIN resources r ON r.id=q.resource_id"+(studentId==null?"":" WHERE q.student_id=?")+" ORDER BY CASE q.status WHEN 'PENDING' THEN 0 ELSE 1 END,q.id DESC";
        try(var c=database.connect();var q=c.prepareStatement(sql)){if(studentId!=null)q.setInt(1,studentId);try(var rs=q.executeQuery()){while(rs.next())rows.add(new ServiceRequest(rs.getInt("id"),rs.getString("name"),rs.getString("subject"),rs.getString("details"),"",rs.getString("requested_at"),rs.getString("status"),"RESOURCE"));}return rows;}
        catch(SQLException e){throw new HostelException("Could not load resource requests",e);}
    }

    public void setResourceRequestStatus(int requestId,String status){
        if(!List.of("APPROVED","DECLINED").contains(status))throw new HostelException("Choose approve or decline.");
        try(var c=database.connect()){
            c.setAutoCommit(false);
            try(var find=c.prepareStatement("SELECT resource_id,status FROM resource_requests WHERE id=?")){find.setInt(1,requestId);try(var rs=find.executeQuery()){
                if(!rs.next()||!"PENDING".equals(rs.getString("status")))throw new HostelException("This request has already been reviewed.");
                if("APPROVED".equals(status))try(var capacity=c.prepareStatement("SELECT r.quantity,COUNT(CASE WHEN q.status='APPROVED' THEN 1 END) AS used FROM resources r LEFT JOIN resource_requests q ON q.resource_id=r.id WHERE r.id=? GROUP BY r.id")){capacity.setInt(1,rs.getInt("resource_id"));try(var slots=capacity.executeQuery()){if(!slots.next()||slots.getInt("used")>=slots.getInt("quantity"))throw new HostelException("No units are currently available.");}}
            }}
            try(var update=c.prepareStatement("UPDATE resource_requests SET status=? WHERE id=? AND status='PENDING'")){update.setString(1,status);update.setInt(2,requestId);update.executeUpdate();}c.commit();
        }catch(HostelException e){throw e;}catch(SQLException e){throw new HostelException("Could not review resource request",e);}
    }

    public void returnResourceRequest(int requestId,int studentId){
        try(var c=database.connect();var q=c.prepareStatement("UPDATE resource_requests SET status='RETURNED' WHERE id=? AND student_id=? AND status='APPROVED'")){
            q.setInt(1,requestId);q.setInt(2,studentId);if(q.executeUpdate()==0)throw new HostelException("Only an approved resource request can be returned.");
        }catch(HostelException e){throw e;}catch(SQLException e){throw new HostelException("Could not return resource",e);}
    }

    public void reportMaintenance(int studentId,String category,String details){
        if(category==null||category.isBlank()||details==null||details.isBlank())throw new HostelException("Choose an issue type and describe the problem.");
        try(var c=database.connect();var q=c.prepareStatement("INSERT INTO maintenance_requests(student_id,room_id,category,details,reported_at) VALUES(?,(SELECT room_id FROM allocations WHERE student_id=?),?,?,?)")){
            q.setInt(1,studentId);q.setInt(2,studentId);q.setString(3,category.trim());q.setString(4,details.trim());q.setString(5,LocalDate.now().toString());q.executeUpdate();
        }catch(SQLException e){throw new HostelException("Could not submit maintenance request",e);}
    }

    public List<ServiceRequest> maintenanceRequests(Integer studentId){
        List<ServiceRequest> rows=new ArrayList<>();String sql="SELECT m.id,s.name,m.category,m.details,COALESCE(r.block||' / '||r.number,'Unassigned') AS room,m.reported_at,m.status FROM maintenance_requests m JOIN students s ON s.id=m.student_id LEFT JOIN rooms r ON r.id=m.room_id"+(studentId==null?"":" WHERE m.student_id=?")+" ORDER BY CASE m.status WHEN 'OPEN' THEN 0 WHEN 'IN_PROGRESS' THEN 1 ELSE 2 END,m.id DESC";
        try(var c=database.connect();var q=c.prepareStatement(sql)){if(studentId!=null)q.setInt(1,studentId);try(var rs=q.executeQuery()){while(rs.next())rows.add(new ServiceRequest(rs.getInt("id"),rs.getString("name"),rs.getString("category"),rs.getString("details"),rs.getString("room"),rs.getString("reported_at"),rs.getString("status"),"MAINTENANCE"));}return rows;}
        catch(SQLException e){throw new HostelException("Could not load maintenance requests",e);}
    }

    public void setMaintenanceStatus(int requestId,String status,String adminNote){
        if(!List.of("IN_PROGRESS","RESOLVED").contains(status))throw new HostelException("Choose an available maintenance status.");
        try(var c=database.connect();var q=c.prepareStatement("UPDATE maintenance_requests SET status=?,admin_note=? WHERE id=? AND status<>'RESOLVED'")){
            q.setString(1,status);q.setString(2,adminNote==null?"":adminNote.trim());q.setInt(3,requestId);if(q.executeUpdate()==0)throw new HostelException("Select an open request first.");
        }catch(HostelException e){throw e;}catch(SQLException e){throw new HostelException("Could not update maintenance request",e);}
    }

    public List<Payment> payments(Integer studentId){
        List<Payment> rows=new ArrayList<>();String sql="SELECT p.id,s.name,p.amount,p.paid_on,p.note FROM payments p JOIN students s ON s.id=p.student_id"+(studentId==null?"":" WHERE p.student_id=?")+" ORDER BY p.paid_on DESC,p.id DESC";
        try(var c=database.connect();var q=c.prepareStatement(sql)){if(studentId!=null)q.setInt(1,studentId);try(var rs=q.executeQuery()){while(rs.next())rows.add(new Payment(rs.getInt("id"),rs.getString("name"),rs.getDouble("amount"),LocalDate.parse(rs.getString("paid_on")),rs.getString("note")));}return rows;}
        catch(SQLException e){throw new HostelException("Could not load payments",e);}
    }

    public int pendingApplications(){return scalar("SELECT COUNT(*) FROM applications WHERE status='PENDING'");}
    public int openMaintenance(){return scalar("SELECT COUNT(*) FROM maintenance_requests WHERE status<>'RESOLVED'");}
    public int occupiedBeds(){return scalar("SELECT COUNT(*) FROM allocations");}
    public int residentCount(){return scalar("SELECT COUNT(*) FROM students");}
    public int availableBedCount(){return scalar("SELECT COALESCE((SELECT SUM(capacity) FROM rooms),0)-(SELECT COUNT(*) FROM allocations)");}
    public double totalPaid(Integer studentId){
        String sql="SELECT COALESCE(SUM(amount),0) FROM payments"+(studentId==null?"":" WHERE student_id=?");
        try(var c=database.connect();var q=c.prepareStatement(sql)){if(studentId!=null)q.setInt(1,studentId);try(var rs=q.executeQuery()){return rs.next()?rs.getDouble(1):0;}}catch(SQLException e){throw new HostelException("Could not calculate fee totals",e);}
    }
    private int scalar(String sql){try(var c=database.connect();var q=c.prepareStatement(sql);var rs=q.executeQuery()){return rs.next()?rs.getInt(1):0;}catch(SQLException e){throw new HostelException("Could not load dashboard data",e);}}
}
