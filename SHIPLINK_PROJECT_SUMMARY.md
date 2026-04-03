# ShipLink Project Summary (For Teacher)

## 1) Project Overview
ShipLink is a Java + JDBC chat application with a browser UI and a lightweight HTTP backend.

Core stack:
- Java 21
- Maven
- SQLite (via JDBC)
- Gson (JSON serialization/deserialization)
- com.sun.net.httpserver.HttpServer (embedded HTTP server)

Main goal:
- Build a local chat platform with account login, message storage, contact discovery, user-specific message deletion, account recovery, and admin-only global visibility.

## 2) What Was Implemented

### A) Architecture cleanup
- Set Maven main class to ChatServer so the packaged JAR starts the web server directly.
- Standardized API parameter usage and UI/backend mapping.
- Verified Linux build and run flow with Maven and Java.

### B) Multi-page UI flow (not single-page login)
- Separate login page and chat page.
- Root route now serves login page.
- Successful user login redirects to chat page.
- Session checks added on client side for redirect behavior.

Pages now used:
- login page for authentication
- chat page for normal user messaging
- master page for admin-only global view

### C) Core chat features
- User account creation (auto-create on first valid login payload).
- Existing user credential verification.
- Send and read messages from SQLite.
- Fetch recent messages and own messages.
- Search users and validate whether username exists.
- Contact list retrieval.

### D) Soft delete behavior (per user)
- Added deletion tracking table.
- Deleting a message hides it only for the deleting user.
- Message still exists for other users unless they delete from their own view.

### E) Forgot password and recovery flow
- New account creation requires:
  - recovery question
  - recovery answer
- Added recovery APIs:
  - fetch user recovery question
  - verify answer + reset password
- Login page now includes forgot-password flow:
  - enter username
  - get question
  - submit answer + new password

### F) Admin master set with authenticator-based access
- Added admin-only access path using TOTP from authenticator app.
- Admin logs in with:
  - username: admin
  - password field: current 6-digit authenticator code
- Added protected admin master endpoint returning:
  - all users
  - all chats
- Added admin master page to display global dataset.
- Added short-lived admin token system (15 min TTL) for protected access.

### G) Branding
- Renamed UI branding from old label to ShipLink across pages.

## 3) JDBC Concepts Used in This Project

### 3.1 Driver and connection URL
- Uses SQLite JDBC driver dependency.
- JDBC URL pattern used:
  - jdbc:sqlite:/absolute/path/to/chat.db

### 3.2 Connection handling
- Each DAO operation opens a connection through DriverManager.
- try-with-resources is used so connections/statements/result sets are automatically closed.
- This avoids resource leaks.

### 3.3 Schema initialization and migration
- On startup, schema is initialized with CREATE TABLE IF NOT EXISTS.
- Backward-compatible migration style added using ALTER TABLE for new recovery columns.
- This allows upgrading old databases without dropping data.

### 3.4 PreparedStatement usage
- All SQL operations use PreparedStatement with placeholders.
- Benefits:
  - prevents SQL injection
  - safer parameter binding
  - cleaner query construction

### 3.5 CRUD patterns with JDBC
- Create:
  - insert users
  - insert messages
  - insert deletions (soft delete marker)
- Read:
  - fetch recent messages
  - fetch own messages
  - fetch contacts
  - fetch all users/messages for admin
- Update:
  - update password during recovery reset
- No hard delete for messages (soft-delete design used)

### 3.6 ResultSet mapping to domain model
- Query rows are converted to Message objects in DAO.
- This separates persistence logic from API layer logic.

### 3.7 Filtering and visibility rules in SQL
- User-specific hidden messages implemented with subquery against deletions table.
- Contact extraction uses DISTINCT author and ordering.

## 4) Database Design Summary

Tables:
- messages
  - id (PK, autoincrement)
  - author
  - payload
  - created_at
- users
  - handle (PK)
  - secret (hashed password)
  - recovery_question
  - recovery_secret (hashed recovery answer)
- deletions
  - message_id
  - deleted_by
  - composite PK (message_id, deleted_by)

## 5) Security Decisions
- Passwords are not stored in plain text.
- Password hash is generated with SHA-256 (salted by handle in current implementation).
- Recovery answers are also hashed before storage.
- Admin global view is protected by TOTP-based second factor and temporary token.

## 6) API Surface (High-Level)

User/chat APIs:
- login
- send message
- recent messages
- own messages
- contacts
- user search
- soft delete

Recovery APIs:
- get recovery question
- reset password

Admin APIs:
- admin master dataset (requires valid admin token)

## 7) What Was Validated During Development
- Build success on Linux.
- Server startup success on port 5050.
- Login/create behavior.
- Wrong-password rejection.
- Soft delete visibility per user.
- Recovery question fetch and password reset.
- Admin TOTP login path and token-protected master retrieval.

## 8) Educational Value (What this project demonstrates)
- Practical JDBC integration in a full app (not just isolated SQL snippets).
- End-to-end backend + frontend coordination.
- Data modeling for access control and soft deletion.
- Incremental schema evolution with backward compatibility.
- Authentication and role-based privileged access.

## 9) Possible Next Improvements
- Replace custom SHA-256 password handling with BCrypt/Argon2.
- Move admin token from query param to Authorization header.
- Add server-side session management for users.
- Add message-to-message recipient model for direct chats.
- Add tests for DAO and auth/recovery endpoints.

## 10) A to Z Steps (Complete Demo Flow)

- A: Install prerequisites: Java 21, Maven, and Git.
- B: Clone the repository from GitHub.
- C: Open terminal in project root.
- D: Build project with `mvn clean package`.
- E: Ensure the shaded JAR exists in `target/`.
- F: (Optional) Install Google Authenticator on phone.
- G: Add TOTP setup key in app as account "ShipLink Admin".
- H: Use key `JBSWY3DPEHPK3PXP` (or your own secret).
- I: Start app quickly on Linux with `./start-chat-ui.sh`.
- J: Or run manually with `java -jar target/advanced-chat-1.0-SNAPSHOT-jar-with-dependencies.jar`.
- K: Open browser at `http://localhost:5050`.
- L: Login as normal user (new users auto-created).
- M: For new user, fill recovery question and recovery answer.
- N: Send messages and verify they appear in recent feed.
- O: Search users and confirm valid/invalid username behavior.
- P: Check contacts list auto-populates from chats.
- Q: Soft delete a message and verify it disappears only for you.
- R: Use forgot password flow from login page.
- S: Enter username, fetch recovery question, submit answer + new password.
- T: Re-login with new password to confirm reset worked.
- U: For admin access, use username `admin`.
- V: In password box, enter current 6-digit authenticator code.
- W: Confirm redirect to master page (`/master.html`).
- X: Verify master page shows all users and all chats.
- Y: Stop server with `kill "$(cat .chat-server.pid)"` (if script used).
- Z: Push code to GitHub and show green CI build in Actions tab.
