# Hostel Resource Management System (Java EWA)

A small JavaFX desktop application for UCS553. It has a JavaFX interface, a Core Java service layer, JDBC DAOs, and a local SQLite database. The feature set follows the hostel/PG mini-project in the syllabus: student CRUD, room creation and capacity checked allocation, fee payment recording/removal, CSV export, and a dashboard.

## Run on macOS with VS Code

1. Install a current JDK (21 or newer), VS Code, and the **Extension Pack for Java**. Open this `hostel-java` folder in VS Code.
2. In VS Code, open **Terminal → New Terminal**. Check Java with `java -version`.
3. Install Maven if needed: `brew install maven`; then check `mvn -version`.
4. Run `mvn clean javafx:run` in the project folder. On first run Maven downloads JavaFX and SQLite JDBC libraries. The database is created at `data/hostel.db` and loaded with 120 distinct demo students, 30 rooms, up to 96 initial room allocations, and 150 fee payments. The seed runs once for an empty database and does not overwrite existing student data.

The project targets Java 21 bytecode. Its JavaFX controls and SQLite driver are declared in `pom.xml`; Maven resolves the matching libraries for macOS. No separate database server is needed.

## Teach it in small steps

1. **Application entry and objects** — start at `HostelApp.java`. JavaFX calls `start(Stage)`; `Stage` is the window. `Student`, `Room`, and `Payment` are objects that hold domain data.
2. **Packages and interfaces** — `model`, `dao`, `service`, `db`, and `exception` packages each have one job. `HostelApp` builds the interface. DAO interfaces describe persistence operations; JDBC implementations fulfil them.
3. **Collections and generics** — DAOs return `List<Student>` and `List<Room>`. `TableView<Student>` is also parameterized, so rows are checked as student objects at compile time.
4. **Exceptions and validation** — the service checks required fields and room capacity before persistence. `HostelException` gives the UI one readable error to show in an alert.
5. **JDBC and transactions** — `Database` opens SQLite connections; DAO methods use `PreparedStatement` and `ResultSet`. Room allocation uses a transaction so occupancy and allocation are saved together.
6. **Configuration and logging** — `app.properties` selects the database location. `java.util.logging` records startup and failures in `logs/hostel.log`.
7. **JavaFX events** — buttons and form fields are connected to handlers in `HostelApp`; handlers call services, then reload the tables.
8. **File handling** — the payment screen writes a CSV report with `Files.newBufferedWriter` and a save dialog.

## First walkthrough

Run the app, select **Students**, add a student, and observe the new row. Select that row to edit or remove it. Select **Rooms**, add a room with capacity 2, then use **Allocate** to assign a student. Try allocating more residents than the room can hold, then release one allocation. Record a fee payment, export the table as CSV, and observe the dashboard refresh. Restart the app: the SQLite data remains.

## Project map

```text
src/main/java/edu/hostel/
  HostelApp.java           JavaFX entry point and event handlers
  model/                   Student, Room, Payment data classes
  dao/                     DAO interfaces and SQLite implementations
  service/                 validation and hostel rules
  db/                      SQLite connection and schema setup
  exception/               user-facing application exception
src/main/resources/        application properties
data/                      created local database (ignored by Git)
logs/                      created application log (ignored by Git)
```

## Troubleshooting

- If `mvn` is not found, install Maven with `brew install maven`, close and reopen the VS Code terminal, and retry.
- If JavaFX reports a version/platform issue, confirm `java -version` is 21 or newer and run `mvn clean javafx:run` so Maven refreshes resolved artifacts.
- To reset sample data, close the app and remove `data/hostel.db`; the next run creates a fresh database. This deletes the local records.

The original GitHub project is a Node/Express resource-booking system. This Java project reuses its hostel-resource-management idea and the student/resource/booking model, and adapts it to the UCS553 hostel resident, room, allocation, and fee-tracking scope.
