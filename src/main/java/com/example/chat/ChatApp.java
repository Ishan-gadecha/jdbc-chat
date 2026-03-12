package com.example.chat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Scanner;

public final class ChatApp {
    private static final DateTimeFormatter OUTPUT_FORMAT = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss");
    private final Scanner console = new Scanner(System.in);
    private final ChatDao chatDao;
    private final String identity;

    public ChatApp(Path dbPath) throws SQLException, IOException {
        Files.createDirectories(dbPath.getParent());
        this.chatDao = new ChatDao(dbPath);
        this.chatDao.initSchema();
        this.identity = promptHandle();
    }

    public static void main(String[] args) {
        Path store = Paths.get(System.getProperty("user.home"), ".advanced-chat", "chat.db");
        System.out.println("Starting JDBC-backed chat client...");
        try {
            new ChatApp(store).run();
        } catch (Exception ex) {
            System.err.println("Failed to start chat: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private void run() {
        while (true) {
            displayMenu();
            String choice = console.nextLine().trim();
            switch (choice) {
                case "1" -> sendMessage();
                case "2" -> showRecent();
                case "3" -> searchByAuthor();
                case "4" -> showOwnStats();
                case "5" -> {
                    System.out.println("Goodbye " + identity + " 👋");
                    return;
                }
                default -> System.out.println("Pick a number between 1 and 5.");
            }
        }
    }

    private void displayMenu() {
        System.out.println();
        System.out.println("=== MENU ===");
        System.out.println("1) Send message");
        System.out.println("2) Show recent conversation");
        System.out.println("3) Find messages by user");
        System.out.println("4) Show my latest posts");
        System.out.println("5) Exit");
        System.out.print("Select an option: ");
    }

    private void sendMessage() {
        System.out.print("Your message: ");
        String text = console.nextLine().trim();
        if (text.isEmpty()) {
            System.out.println("No message provided, returning to menu.");
            return;
        }
        try {
            chatDao.storeMessage(identity, text);
            System.out.println("Stored message at " + OUTPUT_FORMAT.format(java.time.LocalDateTime.now()));
        } catch (SQLException ex) {
            System.err.println("Failed to store message: " + ex.getMessage());
        }
    }

    private void showRecent() {
        List<Message> recent = fetchSafely(() -> chatDao.fetchRecent(10));
        if (recent == null || recent.isEmpty()) {
            System.out.println("No messages yet. Be the first!");
            return;
        }
        System.out.println("-- Recent posts --");
        recent.stream()
                .map(this::format)
                .forEach(System.out::println);
    }

    private void searchByAuthor() {
        System.out.print("Search handle prefix: ");
        String prefix = console.nextLine().trim();
        if (prefix.isEmpty()) {
            System.out.println("Prefix cannot be empty.");
            return;
        }
        List<Message> filtered = fetchSafely(() -> chatDao.fetchByAuthor(prefix, 15));
        if (filtered == null || filtered.isEmpty()) {
            System.out.println("No matches for " + prefix);
            return;
        }
        filtered.stream()
                .map(this::format)
                .forEach(System.out::println);
    }

    private void showOwnStats() {
        List<Message> latest = fetchSafely(() -> chatDao.fetchByAuthor(identity, 5));
        if (latest == null || latest.isEmpty()) {
            System.out.println("You have not posted anything yet.");
            return;
        }
        System.out.println("== Your latest posts ==");
        latest.stream()
                .map(this::format)
                .forEach(System.out::println);
    }

    private String format(Message message) {
        return String.format("[%s] %s: %s",
                OUTPUT_FORMAT.format(message.getTimestamp()),
                message.getAuthor(),
                message.getText());
    }

    private List<Message> fetchSafely(SQLSupplier<List<Message>> supplier) {
        try {
            return supplier.get();
        } catch (SQLException ex) {
            System.err.println("Database query failed: " + ex.getMessage());
            return List.of();
        }
    }

    private String promptHandle() throws IOException {
        System.out.print("What handle should we call you? ");
        String handle = console.nextLine().trim();
        if (handle.isEmpty()) {
            throw new IOException("Handle cannot be empty");
        }
        return handle;
    }

    @FunctionalInterface
    private interface SQLSupplier<T> {
        T get() throws SQLException;
    }
}
