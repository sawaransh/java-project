package edu.hostel;

import edu.hostel.dao.SqlitePaymentDao;
import edu.hostel.dao.SqliteRoomDao;
import edu.hostel.dao.SqliteStudentDao;
import edu.hostel.db.Database;
import edu.hostel.exception.HostelException;
import edu.hostel.model.ApplicationRecord;
import edu.hostel.model.Payment;
import edu.hostel.model.ResidentRecord;
import edu.hostel.model.ResourceItem;
import edu.hostel.model.Room;
import edu.hostel.model.ServiceRequest;
import edu.hostel.model.Student;
import edu.hostel.model.UserSession;
import edu.hostel.service.AuthService;
import edu.hostel.service.HostelService;
import edu.hostel.service.HostelWorkflowService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import javafx.application.Application;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class HostelApp extends Application {
    private static final Logger LOGGER = Logger.getLogger(HostelApp.class.getName());
    private Stage stage;
    private AuthService auth;
    private HostelService hostel;
    private HostelWorkflowService workflow;
    private UserSession session;
    private VBox pageContent;
    private Label pageTitle;
    private Label pageSubtitle;

    @Override public void start(Stage primaryStage) {
        stage = primaryStage;
        try {
            configureLogging();
            Database database = new Database();
            auth = new AuthService(database);
            hostel = new HostelService(new SqliteStudentDao(database), new SqliteRoomDao(database), new SqlitePaymentDao(database));
            workflow = new HostelWorkflowService(database);
            showLogin();
            LOGGER.info("Hostel management application initialized");
        } catch (Exception e) {
            LOGGER.severe("Startup failed: " + e.getMessage());
            showError("Startup problem", e.getMessage());
        }
    }

    private void showLogin() {
        Label brand = new Label("NEST"); brand.getStyleClass().add("brand-large");
        Label leftTitle = new Label("A better way\nto manage hostel life."); leftTitle.getStyleClass().add("login-headline");
        Label leftText = new Label("Applications, room allotments, shared resources and support, together in one place.");
        leftText.getStyleClass().add("login-copy"); leftText.setWrapText(true);
        VBox story = new VBox(22, brand, leftTitle, leftText); story.getStyleClass().add("login-story"); story.setPadding(new Insets(54));

        Label signInTitle = new Label("Welcome back"); signInTitle.getStyleClass().add("section-title");
        Label signInCopy = new Label("Sign in to your hostel workspace"); signInCopy.getStyleClass().add("muted");
        TextField email = input("Email address");
        PasswordField password = new PasswordField(); password.setPromptText("Password"); password.getStyleClass().add("input");
        Button signIn = new Button("Sign in"); signIn.getStyleClass().add("primary-button"); signIn.setMaxWidth(Double.MAX_VALUE);
        Label feedback = new Label(); feedback.getStyleClass().add("error-text");
        signIn.setOnAction(event -> {
            try { signIn(auth.login(email.getText(), password.getText())); }
            catch (Exception e) { feedback.setText(message(e)); }
        });
        password.setOnAction(event -> signIn.fire());
        Button register = new Button("Create a student account"); register.getStyleClass().add("link-button");
        register.setOnAction(event -> showRegistration());
        Label demo = new Label("DEMO ACCESS\nAdmin  admin@hostel.demo  /  Admin123!\nStudent  student097@demo.hostel  /  Student123!");
        demo.getStyleClass().add("demo-note"); demo.setWrapText(true);
        VBox form = new VBox(15, signInTitle, signInCopy, spacer(8), label("EMAIL"), email, label("PASSWORD"), password, feedback, signIn, register, new Separator(), demo);
        form.getStyleClass().add("login-form"); form.setPadding(new Insets(50)); form.setMaxWidth(520);
        BorderPane layout = new BorderPane(); layout.setLeft(story); layout.setCenter(form); BorderPane.setAlignment(form, Pos.CENTER);
        layout.setStyle("-fx-background-color: #f6f7fb;");
        Scene scene = new Scene(layout, 1120, 720); applyStyles(scene);
        stage.setTitle("NEST | Hostel Management"); stage.setMinWidth(960); stage.setMinHeight(650); stage.setScene(scene); stage.show();
    }

    private void showRegistration() {
        Stage dialog = new Stage(); dialog.initOwner(stage); dialog.initModality(Modality.APPLICATION_MODAL); dialog.setTitle("Create student account");
        TextField name = input("Full name"); TextField email = input("Email address"); TextField phone = input("Phone number");
        PasswordField password = new PasswordField(); password.setPromptText("At least 8 characters"); password.getStyleClass().add("input");
        Label error = new Label(); error.getStyleClass().add("error-text");
        Button create = new Button("Create account"); create.getStyleClass().add("primary-button"); create.setMaxWidth(Double.MAX_VALUE);
        create.setOnAction(event -> {
            try { UserSession registered = auth.registerStudent(name.getText(), email.getText(), phone.getText(), password.getText()); dialog.close(); signIn(registered); }
            catch (Exception e) { error.setText(message(e)); }
        });
        VBox card = new VBox(12, new Label("Student registration"), new Label("Create an account to apply for hostel accommodation."), name, email, phone, password, error, create);
        card.setPadding(new Insets(28)); card.setMaxWidth(460); card.getStyleClass().add("card");
        StackPaneCenter center = new StackPaneCenter(card); Scene scene = new Scene(center, 540, 500); applyStyles(scene); dialog.setScene(scene); dialog.showAndWait();
    }

    private void signIn(UserSession user) { session = user; showWorkspace(); }

    private void showWorkspace() {
        BorderPane shell = new BorderPane(); shell.getStyleClass().add("shell");
        VBox sidebar = new VBox(9); sidebar.getStyleClass().add("sidebar"); sidebar.setPadding(new Insets(24, 14, 18, 14));
        HBox logo = new HBox(10, new Label("N"), new VBox(2, new Label("NEST"), new Label("HOSTEL SERVICES")));
        logo.getStyleClass().add("logo-mark");
        sidebar.getChildren().addAll(logo, spacer(20), label("WORKSPACE"));
        List<String> navigation = session.isAdmin()
                ? List.of("Overview", "Applications", "Residents", "Rooms", "Resources", "Fee ledger", "Maintenance")
                : List.of("My overview", "My application", "Shared resources", "My payments", "Report an issue");
        for (String item : navigation) {
            Button nav = new Button(item); nav.getStyleClass().add("nav-button"); nav.setMaxWidth(Double.MAX_VALUE);
            nav.setOnAction(event -> showPage(item)); sidebar.getChildren().add(nav);
        }
        Region push = spacer(1); VBox.setVgrow(push, Priority.ALWAYS);
        Label user = new Label(session.displayName() + "\n" + (session.isAdmin() ? "HOSTEL ADMIN" : "STUDENT")); user.getStyleClass().add("user-badge"); user.setWrapText(true);
        Button logout = new Button("Sign out"); logout.getStyleClass().add("nav-button"); logout.setMaxWidth(Double.MAX_VALUE);
        logout.setOnAction(event -> { session = null; showLogin(); });
        sidebar.getChildren().addAll(push, new Separator(), user, logout); shell.setLeft(sidebar);

        pageTitle = new Label(); pageTitle.getStyleClass().add("page-title");
        pageSubtitle = new Label(); pageSubtitle.getStyleClass().add("muted");
        VBox heading = new VBox(4, pageTitle, pageSubtitle); heading.getStyleClass().add("page-heading");
        pageContent = new VBox(18); pageContent.setPadding(new Insets(22, 30, 30, 30));
        ScrollPane scroll = new ScrollPane(pageContent); scroll.setFitToWidth(true); scroll.getStyleClass().add("content-scroll");
        VBox main = new VBox(0, heading, scroll); main.getStyleClass().add("main-area"); VBox.setVgrow(scroll, Priority.ALWAYS);
        shell.setCenter(main);
        Scene scene = new Scene(shell, 1280, 820); applyStyles(scene);
        stage.setMinWidth(1050); stage.setMinHeight(700); stage.setScene(scene); stage.setTitle("NEST | " + session.displayName()); stage.show();
        showPage(session.isAdmin() ? "Overview" : "My overview");
    }

    private void showPage(String name) {
        if (pageTitle == null || pageContent == null) return;
        pageTitle.setText(name);
        pageSubtitle.setText(switch (name) {
            case "Overview", "My overview" -> "A clear view of what needs attention today.";
            case "Applications" -> "Review requests and assign available rooms.";
            case "Residents" -> "Resident directory and current allotment status.";
            case "Rooms" -> "Track capacity and keep the room inventory current.";
            case "Resources", "Shared resources" -> "Requests and facilities available across the hostel.";
            case "Fee ledger", "My payments" -> "Receipts and payment history.";
            case "Maintenance", "Report an issue" -> "Track reported issues through to resolution.";
            default -> "Hostel services";
        });
        pageContent.getChildren().clear();
        switch (name) {
            case "Overview" -> adminOverview();
            case "My overview" -> studentOverview();
            case "Applications" -> applicationsPage();
            case "Residents" -> residentsPage();
            case "Rooms" -> roomsPage();
            case "Resources", "Shared resources" -> resourcesPage();
            case "Fee ledger", "My payments" -> paymentsPage();
            case "Maintenance", "Report an issue" -> maintenancePage();
            case "My application" -> myApplicationPage();
            default -> pageContent.getChildren().add(new Label("Page unavailable"));
        }
    }

    private void adminOverview() {
        int residents=workflow.residentCount(), occupied=workflow.occupiedBeds(), available=workflow.availableBedCount();
        HBox cards=new HBox(14,
                metric("RESIDENTS",String.valueOf(residents),"All registered students"),
                metric("OCCUPIED BEDS",String.valueOf(occupied),available+" beds remain"),
                metric("APPLICATIONS",String.valueOf(workflow.pendingApplications()),"Awaiting review"),
                metric("OPEN ISSUES",String.valueOf(workflow.openMaintenance()),"Need follow up"));
        pageContent.getChildren().add(cards);
        Label welcome=new Label("Good day, Hostel team");welcome.getStyleClass().add("section-title");
        pageContent.getChildren().addAll(welcome,new Label("Use Applications to match pending requests with available beds. Review maintenance and shared resource requests as they arrive."));
        pageContent.getChildren().addAll(new HBox(12, actionCard("Review applications","Review student preferences and complete room allotments.","Applications"), actionCard("Manage residents","Search the directory and review room assignments.","Residents")));
        pageContent.getChildren().add(actionCard("View maintenance","Track open student issues.","Maintenance"));
    }

    private void studentOverview() {
        ApplicationRecord application=workflow.myApplication(session.studentId());
        String room=workflow.myRoom(session.studentId());
        String status=application==null?(room.startsWith("No room")?"Not submitted":"Allotted"):application.status().replace('_',' ');
        HBox cards=new HBox(14, metric("APPLICATION",status,"Current review status"), metric("ROOM",room,"Your current allotment"), metric("FEES PAID",rupees(workflow.totalPaid(session.studentId())),"Recorded receipts"));
        pageContent.getChildren().add(cards);
        Label heading=new Label("Welcome, "+session.displayName().split(" ")[0]);heading.getStyleClass().add("section-title");
        pageContent.getChildren().addAll(heading,new Label("Start an accommodation application, reserve a shared resource, or send an issue to the hostel team."));
        pageContent.getChildren().addAll(new HBox(12,actionCard("Accommodation","Submit or check your hostel application.","My application"),actionCard("Shared facilities","Request a shared resource.","Shared resources"),actionCard("Get help","Report a room or facility issue.","Report an issue")));
    }

    private void myApplicationPage() {
        ApplicationRecord application=workflow.myApplication(session.studentId());
        VBox current=new VBox(10);current.getStyleClass().add("card");
        String room=workflow.myRoom(session.studentId());
        Label state=new Label(application==null?(room.startsWith("No room")?"No application submitted":"Already allotted"):application.status().replace('_',' '));state.getStyleClass().add("status-pill");
        current.getChildren().addAll(new Label("APPLICATION STATUS"),state,new Label("Room allotment: "+workflow.myRoom(session.studentId())));
        if(application==null&&!room.startsWith("No room")){pageContent.getChildren().addAll(current,new Label("Your room is already assigned. Contact the hostel office if you need a transfer."));return;}
        TextField block=input("Preferred block (for example A or B)");
        TextArea notes=new TextArea();notes.setPromptText("Room preferences or accessibility notes (optional)");notes.setPrefRowCount(3);notes.getStyleClass().add("input");notes.setWrapText(true);
        Button submit=new Button(application==null?"Submit application":"Update and resubmit");submit.getStyleClass().add("primary-button");
        submit.setOnAction(event->run(()->{workflow.submitApplication(session.studentId(),block.getText(),notes.getText());showPage("My application");notice("Application submitted","Your request is now in the hostel team's review queue.");}));
        current.getChildren().addAll(new Label("PREFERRED BLOCK"),block,new Label("NOTES"),notes,submit);
        pageContent.getChildren().addAll(new Label("Apply for a hostel room"),current);
    }

    private void applicationsPage() {
        TableView<ApplicationRecord> table=new TableView<>();
        table.getColumns().add(column("Student","studentName",ApplicationRecord::studentName,170));
        table.getColumns().add(column("Email","email",ApplicationRecord::email,230));
        table.getColumns().add(column("Preference","preferredBlock",ApplicationRecord::preferredBlock,110));
        table.getColumns().add(column("Submitted","date",ApplicationRecord::submittedAt,130));
        table.getColumns().add(column("Status","status",ApplicationRecord::status,130));
        table.getColumns().add(column("Notes","notes",ApplicationRecord::notes,260));
        table.setItems(FXCollections.observableArrayList(workflow.applications().stream().filter(a->List.of("PENDING","WAITLISTED").contains(a.status())).toList())); table.setPrefHeight(430);
        ComboBox<Room> rooms=new ComboBox<>(FXCollections.observableArrayList(workflow.availableRooms()));rooms.setPromptText("Select available room");rooms.setPrefWidth(250);
        Button allot=new Button("Approve & allot room");allot.getStyleClass().add("primary-button");
        allot.setOnAction(event->run(()->{ApplicationRecord row=selected(table,"Select an application first.");if(rooms.getValue()==null)throw new HostelException("Choose an available room.");workflow.allotApplication(row.id(),rooms.getValue().getId());showPage("Applications");}));
        Button waitlist=new Button("Waitlist");waitlist.getStyleClass().add("secondary-button");waitlist.setOnAction(event->reviewApplication(table,"WAITLISTED"));
        Button reject=new Button("Decline");reject.getStyleClass().add("secondary-button");reject.setOnAction(event->reviewApplication(table,"REJECTED"));
        pageContent.getChildren().addAll(section("APPLICATION QUEUE","Select a request, review its preference, then assign an open room."),table,new HBox(10,rooms,allot,waitlist,reject));
    }

    private void reviewApplication(TableView<ApplicationRecord> table,String status){run(()->{workflow.setApplicationStatus(selected(table,"Select an application first.").id(),status);showPage("Applications");});}

    private void residentsPage() {
        TextField search=input("Search by name, email, or room");
        TableView<ResidentRecord> table=new TableView<>();
        table.getColumns().add(column("Name","name",ResidentRecord::name,180));table.getColumns().add(column("Email","email",ResidentRecord::email,240));
        table.getColumns().add(column("Phone","phone",ResidentRecord::phone,150));table.getColumns().add(column("Room","room",ResidentRecord::room,160));table.getColumns().add(column("Allotted","date",ResidentRecord::allocatedOn,140));
        List<ResidentRecord> all=workflow.residents();table.setItems(FXCollections.observableArrayList(all));table.setPrefHeight(470);
        search.textProperty().addListener((o,old,value)->table.setItems(FXCollections.observableArrayList(all.stream().filter(r->(r.name()+r.email()+r.room()).toLowerCase().contains(value.toLowerCase())).toList())));
        Button release=new Button("Release room for selected resident");release.getStyleClass().add("secondary-button");release.setOnAction(event->run(()->{ResidentRecord resident=selected(table,"Select a resident first.");hostel.deallocate(resident.studentId());showPage("Residents");}));
        pageContent.getChildren().addAll(section("RESIDENT DIRECTORY","Search current residents and manage their room assignment."),search,table,release);
    }

    private void roomsPage() {
        TableView<Room> table=new TableView<>();table.getColumns().add(column("Block","block",Room::getBlock,180));table.getColumns().add(column("Room","number",Room::getNumber,180));
        table.getColumns().add(column("Capacity","capacity",r->String.valueOf(r.getCapacity()),150));table.getColumns().add(column("Occupied","occupied",r->String.valueOf(r.getOccupied()),150));
        table.setItems(FXCollections.observableArrayList(hostel.rooms()));table.setPrefHeight(460);
        TextField block=input("Block name");TextField number=input("Room number");TextField capacity=input("Beds");
        Button add=new Button("Add room");add.getStyleClass().add("primary-button");add.setOnAction(event->run(()->{hostel.addRoom(number.getText(),block.getText(),Integer.parseInt(capacity.getText()));showPage("Rooms");}));
        pageContent.getChildren().addAll(section("ROOM INVENTORY","Capacity and occupancy update as you allot or release residents."),table,section("ADD A ROOM","Add a new room to the hostel inventory."),new HBox(10,block,number,capacity,add));
    }

    private void resourcesPage() {
        TableView<ResourceItem> catalog=new TableView<>();catalog.getColumns().add(column("Resource","name",ResourceItem::name,200));catalog.getColumns().add(column("Category","category",ResourceItem::category,140));
        catalog.getColumns().add(column("Location","location",ResourceItem::location,180));catalog.getColumns().add(column("Available","available",r->r.available()+" / "+r.quantity(),110));
        catalog.setItems(FXCollections.observableArrayList(workflow.resources()));catalog.setPrefHeight(session.isAdmin()?300:360);
        pageContent.getChildren().addAll(section("RESOURCE CATALOG","Shared facilities and equipment managed by the hostel."),catalog);
        if(session.isAdmin()){
            TextField name=input("Resource name"),category=input("Category"),location=input("Location"),quantity=input("Quantity");Button add=new Button("Add resource");add.getStyleClass().add("primary-button");
            add.setOnAction(event->run(()->{workflow.addResource(name.getText(),category.getText(),location.getText(),Integer.parseInt(quantity.getText()));showPage("Resources");}));
            pageContent.getChildren().addAll(new HBox(9,name,category,location,quantity,add),section("REQUESTS TO REVIEW","Approve or decline pending student requests."));
            TableView<ServiceRequest> requests=requestTable(workflow.resourceRequests(null));pageContent.getChildren().add(requests);
            Button approve=new Button("Approve selected");approve.getStyleClass().add("primary-button");approve.setOnAction(event->resolveResource(requests,"APPROVED"));
            Button decline=new Button("Decline selected");decline.getStyleClass().add("secondary-button");decline.setOnAction(event->resolveResource(requests,"DECLINED"));
            pageContent.getChildren().add(new HBox(10,approve,decline));
        }else{
            Button request=new Button("Request selected resource");request.getStyleClass().add("primary-button");request.setOnAction(event->run(()->{ResourceItem selected=selected(catalog,"Select a resource first.");workflow.requestResource(session.studentId(),selected.id(),"");showPage("Shared resources");}));
            pageContent.getChildren().addAll(request,section("MY REQUESTS","Requests awaiting review and approved bookings."));
            TableView<ServiceRequest> requests=requestTable(workflow.resourceRequests(session.studentId()));pageContent.getChildren().add(requests);
            Button returned=new Button("Mark selected resource returned");returned.getStyleClass().add("secondary-button");
            returned.setOnAction(event->run(()->{workflow.returnResourceRequest(selected(requests,"Select one of your approved requests first.").id(),session.studentId());showPage("Shared resources");}));
            pageContent.getChildren().add(returned);
        }
    }

    private void resolveResource(TableView<ServiceRequest> table,String status){run(()->{workflow.setResourceRequestStatus(selected(table,"Select a request first.").id(),status);showPage("Resources");});}

    private void paymentsPage() {
        Integer studentId=session.isAdmin()?null:session.studentId();List<Payment> payments=workflow.payments(studentId);
        TableView<Payment> table=new TableView<>();if(session.isAdmin())table.getColumns().add(column("Student","student",Payment::getStudentName,200));
        table.getColumns().add(column("Amount","amount",p->rupees(p.getAmount()),140));table.getColumns().add(column("Date","date",p->p.getPaidOn().toString(),140));table.getColumns().add(column("Description","note",Payment::getNote,310));
        table.setItems(FXCollections.observableArrayList(payments));table.setPrefHeight(460);
        pageContent.getChildren().addAll(metric(session.isAdmin()?"TOTAL RECEIVED":"TOTAL PAID",rupees(workflow.totalPaid(studentId)),"Based on recorded receipts"),table);
        if(session.isAdmin()){
            ComboBox<Student> students=new ComboBox<>(FXCollections.observableArrayList(hostel.students()));students.setPromptText("Student");students.setPrefWidth(280);
            TextField amount=input("Amount (₹)"),note=input("Payment description");Button add=new Button("Record payment");add.getStyleClass().add("primary-button");
            add.setOnAction(event->run(()->{if(students.getValue()==null)throw new HostelException("Choose a student.");hostel.addPayment(students.getValue().getId(),Double.parseDouble(amount.getText()),note.getText());showPage("Fee ledger");}));
            pageContent.getChildren().addAll(section("RECORD A RECEIPT","Record an installment or other hostel payment."),new HBox(10,students,amount,note,add));
        }
        Button export=new Button("Export CSV");export.getStyleClass().add("secondary-button");export.setOnAction(event->exportPayments(payments));pageContent.getChildren().add(export);
    }

    private void maintenancePage() {
        if(!session.isAdmin()){
            ComboBox<String> category=new ComboBox<>(FXCollections.observableArrayList("Plumbing","Electrical","Cleaning","Furniture","Internet","Other"));category.setPromptText("Issue type");category.setPrefWidth(220);
            TextArea details=new TextArea();details.setPromptText("Describe the issue and where it is located");details.setPrefRowCount(3);details.setWrapText(true);details.getStyleClass().add("input");
            Button submit=new Button("Submit issue");submit.getStyleClass().add("primary-button");submit.setOnAction(event->run(()->{if(category.getValue()==null)throw new HostelException("Choose an issue type.");workflow.reportMaintenance(session.studentId(),category.getValue(),details.getText());showPage("Report an issue");}));
            pageContent.getChildren().addAll(section("REPORT A HOSTEL ISSUE","The hostel team can see your room when it is allotted."),category,details,submit,section("MY REPORTS","Track updates from the hostel team."),requestTable(workflow.maintenanceRequests(session.studentId())));
        }else{
            TableView<ServiceRequest> requests=requestTable(workflow.maintenanceRequests(null));requests.setPrefHeight(500);
            TextField note=input("Work update / resolution note");Button progress=new Button("Mark in progress");progress.getStyleClass().add("secondary-button");
            progress.setOnAction(event->updateMaintenance(requests,"IN_PROGRESS",note.getText()));Button resolve=new Button("Resolve issue");resolve.getStyleClass().add("primary-button");
            resolve.setOnAction(event->updateMaintenance(requests,"RESOLVED",note.getText()));
            pageContent.getChildren().addAll(section("SERVICE QUEUE","Review requests, record progress, and close resolved issues."),requests,note,new HBox(10,progress,resolve));
        }
    }

    private void updateMaintenance(TableView<ServiceRequest> table,String status,String note){run(()->{workflow.setMaintenanceStatus(selected(table,"Select a report first.").id(),status,note);showPage("Maintenance");});}

    private TableView<ServiceRequest> requestTable(List<ServiceRequest> rows){
        TableView<ServiceRequest> table=new TableView<>();table.getColumns().add(column("Student","student",ServiceRequest::studentName,170));
        table.getColumns().add(column("Request","subject",ServiceRequest::subject,150));table.getColumns().add(column("Details","details",ServiceRequest::details,240));
        table.getColumns().add(column("Room","room",ServiceRequest::room,120));table.getColumns().add(column("Date","date",ServiceRequest::createdAt,115));table.getColumns().add(column("Status","status",ServiceRequest::status,120));
        table.setItems(FXCollections.observableArrayList(rows));table.setPrefHeight(300);return table;
    }

    private void exportPayments(List<Payment> payments){
        FileChooser chooser=new FileChooser();chooser.setTitle("Save fee ledger");chooser.setInitialFileName("hostel-payments.csv");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files","*.csv"));var file=chooser.showSaveDialog(stage);if(file==null)return;
        try(var writer=Files.newBufferedWriter(file.toPath(),StandardCharsets.UTF_8)){
            writer.write("student,amount,paid_on,note\n");for(Payment p:payments)writer.write("\""+csv(p.getStudentName())+"\","+p.getAmount()+","+p.getPaidOn()+",\""+csv(p.getNote())+"\"\n");
            notice("Export complete","The fee ledger has been saved as a CSV file.");
        }catch(IOException e){showError("Could not save the CSV file",e.getMessage());}
    }

    private <T> TableColumn<T,String> column(String title,String id,Function<T,String> value,double width){
        TableColumn<T,String> c=new TableColumn<>(title);c.setId(id);c.setPrefWidth(width);c.setCellValueFactory(data->new SimpleStringProperty(value.apply(data.getValue())));return c;
    }
    private <T> T selected(TableView<T> table,String error){T row=table.getSelectionModel().getSelectedItem();if(row==null)throw new HostelException(error);return row;}
    private VBox metric(String title,String value,String note){Label k=new Label(title);k.getStyleClass().add("metric-label");Label v=new Label(value);v.getStyleClass().add("metric-value");Label n=new Label(note);n.getStyleClass().add("muted");VBox card=new VBox(9,k,v,n);card.getStyleClass().add("metric-card");HBox.setHgrow(card,Priority.ALWAYS);card.setMinWidth(190);return card;}
    private VBox actionCard(String title,String description,String target){Label h=new Label(title);h.getStyleClass().add("card-title");Label body=new Label(description);body.getStyleClass().add("muted");body.setWrapText(true);Button open=new Button("Open section →");open.getStyleClass().add("link-button");open.setOnAction(e->showPage(target));VBox card=new VBox(11,h,body,open);card.getStyleClass().add("card");HBox.setHgrow(card,Priority.ALWAYS);card.setMinWidth(230);return card;}
    private VBox section(String title,String caption){Label h=new Label(title);h.getStyleClass().add("eyebrow");Label c=new Label(caption);c.getStyleClass().add("muted");return new VBox(5,h,c);}
    private Label label(String text){Label l=new Label(text);l.getStyleClass().add("field-label");return l;}
    private TextField input(String prompt){TextField f=new TextField();f.setPromptText(prompt);f.getStyleClass().add("input");return f;}
    private Region spacer(double height){Region r=new Region();r.setMinHeight(height);r.setPrefHeight(height);return r;}
    private String rupees(double amount){return "₹"+String.format("%,.0f",amount);}
    private String csv(String text){return text.replace("\"","\"\"");}
    private String message(Exception e){return e.getMessage()==null?"Please check the information and try again.":e.getMessage();}
    private void run(Runnable action){try{action.run();}catch(Exception e){LOGGER.warning(e.getMessage());showError("Please check this action",message(e));}}
    private void notice(String title,String text){Alert a=new Alert(Alert.AlertType.INFORMATION);a.setTitle("NEST");a.setHeaderText(title);a.setContentText(text);a.showAndWait();}
    private void showError(String title,String text){Alert a=new Alert(Alert.AlertType.ERROR);a.setTitle("NEST");a.setHeaderText(title);a.setContentText(text==null?"An unexpected problem occurred.":text);a.showAndWait();}
    private void applyStyles(Scene scene){var sheet=getClass().getResource("/app.css");if(sheet!=null)scene.getStylesheets().add(sheet.toExternalForm());}
    private void configureLogging()throws IOException{Files.createDirectories(Path.of("logs"));FileHandler handler=new FileHandler("logs/hostel.log",true);handler.setFormatter(new SimpleFormatter());LOGGER.addHandler(handler);}
    public static void main(String[] args){launch(args);}

    private static final class StackPaneCenter extends javafx.scene.layout.StackPane {
        StackPaneCenter(Node child){getChildren().add(child);setAlignment(Pos.CENTER);setPadding(new Insets(28));setStyle("-fx-background-color: #f6f7fb;");}
    }
}
