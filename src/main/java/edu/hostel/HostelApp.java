package edu.hostel;

import edu.hostel.db.Database;
import edu.hostel.dao.SqlitePaymentDao;
import edu.hostel.dao.SqliteRoomDao;
import edu.hostel.dao.SqliteStudentDao;
import edu.hostel.exception.HostelException;
import edu.hostel.model.Payment;
import edu.hostel.model.Room;
import edu.hostel.model.Student;
import edu.hostel.service.HostelService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import javafx.application.Application;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.FileChooser;

public class HostelApp extends Application {
    private static final Logger LOGGER = Logger.getLogger(HostelApp.class.getName());
    private HostelService service;
    private TableView<Student> studentTable;
    private TableView<Room> roomTable;
    private TableView<Payment> paymentTable;
    private Label dashboard;
    private ComboBox<Student> allocationStudent;
    private ComboBox<Room> allocationRoom;
    private ComboBox<Student> paymentStudent;

    @Override public void start(Stage stage) {
        try {
            configureLogging();
            Database database = new Database();
            service = new HostelService(new SqliteStudentDao(database), new SqliteRoomDao(database), new SqlitePaymentDao(database));
            TabPane tabs = new TabPane(tab("Dashboard", dashboardPage()), tab("Students", studentPage()), tab("Rooms & allocation", roomPage()), tab("Fee payments", paymentPage()));
            tabs.getTabs().forEach(tab -> tab.setClosable(false));
            refresh();
            stage.setTitle("Hostel Resource Management");
            stage.setScene(new Scene(tabs, 940, 620));
            stage.show();
            LOGGER.info("Hostel application started");
        } catch (Exception e) {
            LOGGER.severe("Startup failed: " + e.getMessage());
            showError(e.getMessage());
        }
    }

    private Tab tab(String name, VBox content) { return new Tab(name, content); }

    private VBox dashboardPage() {
        dashboard = new Label();
        dashboard.setStyle("-fx-font-size: 20px; -fx-padding: 28;");
        return page(new Label("Hostel overview"), dashboard, new Label("Use the tabs to manage residents, rooms, and fee receipts."));
    }

    private VBox studentPage() {
        studentTable = new TableView<>();
        studentTable.getColumns().add(column("ID", "id", 70));
        studentTable.getColumns().add(column("Name", "name", 220));
        studentTable.getColumns().add(column("Phone", "phone", 180));
        studentTable.getColumns().add(column("Email", "email", 350));
        TextField name = field("Student name"); TextField phone = field("Phone"); TextField email = field("Email");
        Button add = new Button("Add student");
        add.setOnAction(event -> run(() -> { service.addStudent(name.getText(), phone.getText(), email.getText()); name.clear(); phone.clear(); email.clear(); refresh(); }));
        Button update = new Button("Update selected");
        update.setOnAction(event -> run(() -> { Student selected = selectedStudent(); service.updateStudent(selected.getId(), name.getText(), phone.getText(), email.getText()); refresh(); }));
        Button remove = new Button("Remove selected");
        remove.setOnAction(event -> run(() -> { service.removeStudent(selectedStudent().getId()); name.clear(); phone.clear(); email.clear(); refresh(); }));
        studentTable.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, selected) -> {
            if (selected != null) { name.setText(selected.getName()); phone.setText(selected.getPhone()); email.setText(selected.getEmail()); }
        });
        return page(new Label("Students"), new HBox(8, name, phone, email, add), new HBox(8, update, remove), studentTable);
    }

    private VBox roomPage() {
        roomTable = new TableView<>();
        roomTable.getColumns().add(column("ID", "id", 70));
        roomTable.getColumns().add(column("Block", "block", 160));
        roomTable.getColumns().add(column("Room", "number", 160));
        roomTable.getColumns().add(column("Capacity", "capacity", 150));
        roomTable.getColumns().add(column("Occupied", "occupied", 150));
        TextField number = field("Room number"); TextField block = field("Block"); TextField capacity = field("Capacity");
        Button add = new Button("Add room");
        add.setOnAction(event -> run(() -> { service.addRoom(number.getText(), block.getText(), Integer.parseInt(capacity.getText())); number.clear(); block.clear(); capacity.clear(); refresh(); }));
        allocationStudent = new ComboBox<>(); allocationStudent.setPromptText("Choose student"); allocationStudent.setPrefWidth(230);
        allocationRoom = new ComboBox<>(); allocationRoom.setPromptText("Choose room"); allocationRoom.setPrefWidth(230);
        Button allocate = new Button("Allocate room");
        allocate.setOnAction(event -> run(() -> {
            if (allocationStudent.getValue() == null || allocationRoom.getValue() == null) throw new HostelException("Choose both a student and room.");
            service.allocate(allocationStudent.getValue().getId(), allocationRoom.getValue().getId()); refresh();
        }));
        Button release = new Button("Release room");
        release.setOnAction(event -> run(() -> {
            if (allocationStudent.getValue() == null) throw new HostelException("Choose the student whose room should be released.");
            service.deallocate(allocationStudent.getValue().getId()); refresh();
        }));
        return page(new Label("Rooms"), new HBox(8, number, block, capacity, add), roomTable,
                new Label("Assign or release a student's room"), new HBox(8, allocationStudent, allocationRoom, allocate, release));
    }

    private VBox paymentPage() {
        paymentTable = new TableView<>();
        paymentTable.getColumns().add(column("ID", "id", 70));
        paymentTable.getColumns().add(column("Student", "studentName", 230));
        paymentTable.getColumns().add(column("Amount", "amount", 160));
        paymentTable.getColumns().add(column("Date", "paidOn", 160));
        paymentTable.getColumns().add(column("Note", "note", 240));
        paymentStudent = new ComboBox<>(); paymentStudent.setPromptText("Choose student"); paymentStudent.setPrefWidth(230);
        TextField amount = field("Amount (₹)"); TextField note = field("Note (optional)");
        Button add = new Button("Record payment");
        add.setOnAction(event -> run(() -> {
            if (paymentStudent.getValue() == null) throw new HostelException("Choose a student first.");
            service.addPayment(paymentStudent.getValue().getId(), Double.parseDouble(amount.getText()), note.getText()); amount.clear(); note.clear(); refresh();
        }));
        Button remove = new Button("Remove selected");
        remove.setOnAction(event -> run(() -> {
            Payment selected = paymentTable.getSelectionModel().getSelectedItem();
            if (selected == null) throw new HostelException("Select a payment row first.");
            service.removePayment(selected.getId()); refresh();
        }));
        Button export = new Button("Export CSV"); export.setOnAction(event -> exportPayments());
        return page(new Label("Fee payments"), new HBox(8, paymentStudent, amount, note, add), new HBox(8, remove, export), paymentTable);
    }

    private VBox page(javafx.scene.Node... nodes) {
        VBox box = new VBox(14, nodes); box.setPadding(new Insets(22));
        for (javafx.scene.Node node : nodes) if (node instanceof TableView<?>) VBox.setVgrow(node, javafx.scene.layout.Priority.ALWAYS);
        return box;
    }

    private TextField field(String prompt) { TextField field = new TextField(); field.setPromptText(prompt); field.setPrefWidth(170); return field; }

    private <T> TableColumn<T, String> column(String title, String property, double width) {
        TableColumn<T, String> column = new TableColumn<>(title); column.setPrefWidth(width);
        column.setCellValueFactory(data -> {
            Object value = switch (property) {
                case "id" -> data.getValue() instanceof Student s ? s.getId() : data.getValue() instanceof Room r ? r.getId() : ((Payment) data.getValue()).getId();
                case "name" -> ((Student) data.getValue()).getName();
                case "phone" -> ((Student) data.getValue()).getPhone();
                case "email" -> ((Student) data.getValue()).getEmail();
                case "block" -> ((Room) data.getValue()).getBlock();
                case "number" -> ((Room) data.getValue()).getNumber();
                case "capacity" -> ((Room) data.getValue()).getCapacity();
                case "occupied" -> ((Room) data.getValue()).getOccupied();
                case "studentName" -> ((Payment) data.getValue()).getStudentName();
                case "amount" -> String.format("₹%.2f", ((Payment) data.getValue()).getAmount());
                case "paidOn" -> ((Payment) data.getValue()).getPaidOn();
                default -> ((Payment) data.getValue()).getNote();
            };
            return new SimpleStringProperty(String.valueOf(value));
        });
        return column;
    }

    private void refresh() {
        var students = service.students(); var rooms = service.rooms(); var payments = service.payments();
        if (studentTable != null) studentTable.setItems(FXCollections.observableArrayList(students));
        if (roomTable != null) roomTable.setItems(FXCollections.observableArrayList(rooms));
        if (paymentTable != null) paymentTable.setItems(FXCollections.observableArrayList(payments));
        if (allocationStudent != null) allocationStudent.setItems(FXCollections.observableArrayList(students));
        if (paymentStudent != null) paymentStudent.setItems(FXCollections.observableArrayList(students));
        if (allocationRoom != null) allocationRoom.setItems(FXCollections.observableArrayList(rooms.stream().filter(r -> r.getOccupied() < r.getCapacity()).toList()));
        if (dashboard != null) {
            int beds = rooms.stream().mapToInt(Room::getCapacity).sum(); int used = rooms.stream().mapToInt(Room::getOccupied).sum();
            double total = payments.stream().mapToDouble(Payment::getAmount).sum();
            dashboard.setText("Students registered: " + students.size() + "\nBeds occupied: " + used + " / " + beds + "\nPayments recorded: " + payments.size() + "\nTotal fees received: ₹" + String.format("%.2f", total));
        }
    }

    private void run(Runnable action) {
        try { action.run(); }
        catch (Exception e) { LOGGER.warning(e.getMessage()); showError(e instanceof HostelException ? e.getMessage() : "Check the values and try again."); }
    }

    private Student selectedStudent() {
        Student selected = studentTable.getSelectionModel().getSelectedItem();
        if (selected == null) throw new HostelException("Select a student row first.");
        return selected;
    }

    private void exportPayments() {
        FileChooser chooser = new FileChooser(); chooser.setTitle("Save fee payments"); chooser.setInitialFileName("hostel-payments.csv");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files", "*.csv"));
        var file = chooser.showSaveDialog(paymentTable.getScene().getWindow());
        if (file == null) return;
        try (var writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
            writer.write("id,student,amount,paid_on,note\n");
            for (Payment payment : service.payments()) {
                writer.write(payment.getId() + ",\"" + csv(payment.getStudentName()) + "\"," + payment.getAmount() + "," + payment.getPaidOn() + ",\"" + csv(payment.getNote()) + "\"\n");
            }
            new Alert(Alert.AlertType.INFORMATION, "Payment report saved.").showAndWait();
        } catch (IOException e) { LOGGER.warning("CSV export failed: " + e.getMessage()); showError("Could not write that CSV file."); }
    }

    private String csv(String value) { return value.replace("\"", "\"\""); }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR); alert.setTitle("Hostel manager"); alert.setHeaderText("Could not complete that action"); alert.setContentText(message == null ? "Unexpected application error." : message); alert.showAndWait();
    }

    private void configureLogging() throws IOException {
        Files.createDirectories(Path.of("logs")); FileHandler handler = new FileHandler("logs/hostel.log", true); handler.setFormatter(new SimpleFormatter()); LOGGER.addHandler(handler);
    }

    public static void main(String[] args) { launch(args); }
}
