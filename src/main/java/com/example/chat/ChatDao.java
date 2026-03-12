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
            + "payload TEXT NOT NULL,"
            + "created_at TEXT NOT NULL)";
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
        }
    }

    public void storeMessage(String author, String payload) throws SQLException {
        String insert = "INSERT INTO messages (author, payload, created_at) VALUES (?, ?, ?)";
        try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(insert)) {
            statement.setString(1, author);
            statement.setString(2, payload);
            statement.setString(3, LocalDateTime.now().toString());
            statement.executeUpdate();
        }
    }

    public List<Message> fetchRecent(int limit) throws SQLException {
        String select = "SELECT id, author, payload, created_at FROM messages ORDER BY created_at DESC LIMIT ?";
        try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(select)) {
            statement.setInt(1, limit);
            try (ResultSet rs = statement.executeQuery()) {
                return toMessages(rs);
            }
        }
    }

    public List<Message> fetchByAuthor(String authorFilter, int limit) throws SQLException {
        String select = "SELECT id, author, payload, created_at FROM messages WHERE author LIKE ? ORDER BY created_at DESC LIMIT ?";
        try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(select)) {
            statement.setString(1, authorFilter + "%");
            statement.setInt(2, limit);
            try (ResultSet rs = statement.executeQuery()) {
                return toMessages(rs);
            }
        }
    }

    private List<Message> toMessages(ResultSet rs) throws SQLException {
        List<Message> messages = new ArrayList<>();
        while (rs.next()) {
            messages.add(new Message(
                    rs.getLong("id"),
                    rs.getString("author"),
                    rs.getString("payload"),
                    LocalDateTime.parse(rs.getString("created_at"))));
        }
        return messages;
    }
}
