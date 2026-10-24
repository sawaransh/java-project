# NEST Hostel Management System

A JavaFX desktop app for managing hostel applications, room allotments, residents, shared resources, fee receipts, and maintenance requests. The interface changes by role so students see their own hostel services while administrators see the hostel-wide queues and records.

## Run on macOS

1. Install JDK 21 or newer, VS Code with the Extension Pack for Java, and Maven (`brew install maven`).
2. Open this project folder in VS Code.
3. In **Terminal → New Terminal**, run:

   ```bash
   mvn clean javafx:run
   ```

On first launch, the app creates `data/hostel.db`, performs any needed schema upgrades, and loads a demonstration database with 120 students, 30 rooms, room assignments, applications, payments, resources, resource requests, and maintenance reports. Existing student records are retained. Demo records are only seeded once.

## Demo accounts

- Administrator: `admin@hostel.demo` / `Admin123!`
- Student with a pending room application: `student097@demo.hostel` / `Student123!`
- Other seeded students use the same password: `Student123!`

Students can also create an account from the sign-in screen. Passwords are stored as salted PBKDF2 hashes. The shared demo passwords are for this classroom project only; change or remove them before using real student information.

## Workflows

### Student

- Submit or resubmit a room application with a preferred block and notes.
- See the application status and current room allotment.
- Browse hostel resources and request shared equipment or facilities.
- View personal payment receipts and export them to CSV.
- Report plumbing, electrical, cleaning, furniture, internet, or other issues and follow their status.

### Administrator

- Review pending and waitlisted applications, then approve and allot an available room in one step. Capacity is checked in a database transaction.
- Search the resident directory and release a room assignment.
- Track room capacity and add rooms.
- Add shared resources and approve or decline student requests. Students can mark approved requests returned.
- Record fee payments and export the fee ledger.
- Move maintenance reports through open, in progress, and resolved.

## How the Java pieces fit

- `HostelApp` contains the JavaFX sign-in flow, role-specific navigation, and event handlers.
- `AuthService` authenticates accounts and creates student accounts; `PasswordUtil` hashes passwords.
- `HostelWorkflowService` enforces application, allotment, shared-resource, and maintenance rules.
- `HostelService` and the DAO classes handle student, room, and payment operations through JDBC.
- `Database` creates and upgrades the SQLite schema, then seeds the demo data once.
- `src/main/resources/app.css` styles the sign-in page, navigation, metric cards, forms, and tables.

## Local files

- SQLite database: `data/hostel.db`
- Application log: `logs/hostel.log`
- Database location: `src/main/resources/app.properties`

To start with a fresh demo database, close the app and remove `data/hostel.db`. The next launch recreates the database and seed data. This also deletes any changes made in the app.
