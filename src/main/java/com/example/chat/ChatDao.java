package com.example.chat;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public final class ChatDao {
    private static final String TABLE_SETUP = "CREATE TABLE IF NOT EXISTS messages ("
            + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
            + "author TEXT NOT NULL,"
            + "recipient TEXT,"
            + "payload TEXT NOT NULL,"
            + "created_at TEXT NOT NULL)";
    private static final String USERS_TABLE_SETUP = "CREATE TABLE IF NOT EXISTS users ("
        + "handle TEXT PRIMARY KEY,"
        + "password_hash TEXT NOT NULL,"
        + "recovery_question TEXT NOT NULL,"
        + "recovery_answer_hash TEXT NOT NULL,"
        + "is_admin INTEGER NOT NULL DEFAULT 0)";
    private static final String CONTACTS_TABLE_SETUP = "CREATE TABLE IF NOT EXISTS contacts ("
        + "owner_handle TEXT NOT NULL,"
        + "contact_handle TEXT NOT NULL,"
        + "PRIMARY KEY (owner_handle, contact_handle))";
    private final String jdbcUrl;

    public ChatDao(Path dbPath) {
        this.jdbcUrl = "jdbc:sqlite:" + dbPath.toAbsolutePath();
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(jdbcUrl);
    }

    public void initSchema() throws SQLException {
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            statement.execute(TABLE_SETUP);
            statement.execute(USERS_TABLE_SETUP);
            statement.execute(CONTACTS_TABLE_SETUP);
            try {
                statement.execute("ALTER TABLE messages ADD COLUMN recipient TEXT");
            } catch (SQLException ignored) {
                // Column already exists for upgraded databases.
            }
        }
    }

    public boolean userExists(String handle) throws SQLException {
        String query = "SELECT 1 FROM users WHERE handle = ?";
        try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, handle);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        }
    }

    public boolean registerUser(String handle, String password, String recoveryQuestion, String recoveryAnswer) throws SQLException {
        String insert = "INSERT INTO users (handle, password_hash, recovery_question, recovery_answer_hash, is_admin) VALUES (?, ?, ?, ?, ?)";
        try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(insert)) {
            statement.setString(1, handle);
            statement.setString(2, PasswordUtil.hash(handle, password));
            statement.setString(3, recoveryQuestion);
            statement.setString(4, PasswordUtil.hash(handle, recoveryAnswer));
            statement.setInt(5, 0);
            return statement.executeUpdate() == 1;
        }
    }

    public boolean validateLogin(String handle, String password) throws SQLException {
        String query = "SELECT password_hash FROM users WHERE handle = ?";
        try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, handle);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return false;
                }
                String expected = rs.getString("password_hash");
                String actual = PasswordUtil.hash(handle, password);
                return expected.equals(actual);
            }
        }
    }

    public boolean isAdmin(String handle) throws SQLException {
        String query = "SELECT is_admin FROM users WHERE handle = ?";
        try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, handle);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() && rs.getInt("is_admin") == 1;
            }
        }
    }

    public String recoveryQuestion(String handle) throws SQLException {
        String query = "SELECT recovery_question FROM users WHERE handle = ?";
        try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, handle);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return rs.getString("recovery_question");
            }
        }
    }

    public boolean resetPassword(String handle, String answer, String newPassword) throws SQLException {
        String select = "SELECT recovery_answer_hash FROM users WHERE handle = ?";
        try (Connection connection = connect(); PreparedStatement selectStmt = connection.prepareStatement(select)) {
            selectStmt.setString(1, handle);
            try (ResultSet rs = selectStmt.executeQuery()) {
                if (!rs.next()) {
                    return false;
                }
                String expected = rs.getString("recovery_answer_hash");
                if (!expected.equals(PasswordUtil.hash(handle, answer))) {
                    return false;
                }
            }

            String update = "UPDATE users SET password_hash = ? WHERE handle = ?";
            try (PreparedStatement updateStmt = connection.prepareStatement(update)) {
                updateStmt.setString(1, PasswordUtil.hash(handle, newPassword));
                updateStmt.setString(2, handle);
                return updateStmt.executeUpdate() == 1;
            }
        }
    }

    public void storeMessage(String author, String payload) throws SQLException {
        storeDirectMessage(author, null, payload);
    }

    public void storeDirectMessage(String author, String recipient, String payload) throws SQLException {
        String insert = "INSERT INTO messages (author, recipient, payload, created_at) VALUES (?, ?, ?, ?)";
        try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(insert)) {
            statement.setString(1, author);
            statement.setString(2, recipient);
            statement.setString(3, payload);
            statement.setString(4, LocalDateTime.now().toString());
            statement.executeUpdate();
        }
    }

    public List<Message> fetchRecent(int limit) throws SQLException {
        String select = "SELECT id, author, recipient, payload, created_at FROM messages ORDER BY created_at DESC LIMIT ?";
        try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(select)) {
            statement.setInt(1, limit);
            try (ResultSet rs = statement.executeQuery()) {
                return toMessages(rs);
            }
        }
    }

    public List<Message> fetchConversation(String userA, String userB, int limit) throws SQLException {
        String select = "SELECT id, author, recipient, payload, created_at FROM messages "
                + "WHERE (author = ? AND recipient = ?) OR (author = ? AND recipient = ?) "
                + "ORDER BY created_at DESC LIMIT ?";
        try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(select)) {
            statement.setString(1, userA);
            statement.setString(2, userB);
            statement.setString(3, userB);
            statement.setString(4, userA);
            statement.setInt(5, limit);
            try (ResultSet rs = statement.executeQuery()) {
                return toMessages(rs);
            }
        }
    }

    public List<Message> fetchForUser(String user, int limit) throws SQLException {
        String select = "SELECT id, author, recipient, payload, created_at FROM messages "
                + "WHERE author = ? OR recipient = ? ORDER BY created_at DESC LIMIT ?";
        try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(select)) {
            statement.setString(1, user);
            statement.setString(2, user);
            statement.setInt(3, limit);
            try (ResultSet rs = statement.executeQuery()) {
                return toMessages(rs);
            }
        }
    }

    public List<Message> fetchByAuthor(String authorFilter, int limit) throws SQLException {
        String select = "SELECT id, author, recipient, payload, created_at FROM messages WHERE author LIKE ? ORDER BY created_at DESC LIMIT ?";
        try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(select)) {
            statement.setString(1, authorFilter + "%");
            statement.setInt(2, limit);
            try (ResultSet rs = statement.executeQuery()) {
                return toMessages(rs);
            }
        }
    }

    public boolean deleteMessage(long messageId, String handle) throws SQLException {
        String delete = "DELETE FROM messages WHERE id = ? AND author = ?";
        try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(delete)) {
            statement.setLong(1, messageId);
            statement.setString(2, handle);
            return statement.executeUpdate() == 1;
        }
    }

    public List<String> fetchContacts(String handle) throws SQLException {
        String select = "SELECT contact_handle FROM contacts WHERE owner_handle = ? ORDER BY contact_handle ASC";
        List<String> contacts = new ArrayList<>();
        try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(select)) {
            statement.setString(1, handle);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    contacts.add(rs.getString("contact_handle"));
                }
            }
        }
        return contacts;
    }

    public void addContact(String ownerHandle, String contactHandle) throws SQLException {
        String insert = "INSERT OR IGNORE INTO contacts (owner_handle, contact_handle) VALUES (?, ?)";
        try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(insert)) {
            statement.setString(1, ownerHandle);
            statement.setString(2, contactHandle);
            statement.executeUpdate();
        }
    }

    public List<String> findUsersByPrefix(String prefix, int limit) throws SQLException {
        String query = "SELECT handle FROM users WHERE handle LIKE ? ORDER BY handle ASC LIMIT ?";
        List<String> users = new ArrayList<>();
        try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, prefix + "%");
            statement.setInt(2, limit);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    users.add(rs.getString("handle"));
                }
            }
        }
        return users;
    }

    public List<String> fetchAllUsers(int limit) throws SQLException {
        String query = "SELECT handle FROM users ORDER BY handle ASC LIMIT ?";
        List<String> users = new ArrayList<>();
        try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, limit);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    users.add(rs.getString("handle"));
                }
            }
        }
        return users;
    }

    public List<Message> fetchAllMessages(int limit) throws SQLException {
        return fetchRecent(limit);
    }

    private List<Message> toMessages(ResultSet rs) throws SQLException {
        List<Message> messages = new ArrayList<>();
        while (rs.next()) {
            messages.add(new Message(
                    rs.getLong("id"),
                    rs.getString("author"),
                    rs.getString("recipient"),
                    rs.getString("payload"),
                    LocalDateTime.parse(rs.getString("created_at"))));
        }
        return messages;
    }
}
