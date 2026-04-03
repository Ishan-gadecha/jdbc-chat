package com.example.chat;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

public final class ChatServer {
    private static final int DEFAULT_PORT = 8080;
    private static final Gson GSON = new Gson();
    private static final Map<String, String> ADMIN_TOKENS = new ConcurrentHashMap<>();

    private ChatServer() {
    }

    public static void main(String[] args) {
        Path store = Paths.get(System.getProperty("user.home"), ".advanced-chat", "chat.db");
        int port = resolvePort();
        try {
            Files.createDirectories(store.getParent());
            ChatDao dao = new ChatDao(store);
            dao.initSchema();
            HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
            server.setExecutor(Executors.newFixedThreadPool(4));
            server.createContext("/", ChatServer::handleStatic);
            server.createContext("/api/login", exchange -> handleLogin(exchange, dao));
            server.createContext("/api/recovery/question", exchange -> handleRecoveryQuestion(exchange, dao));
            server.createContext("/api/recovery/reset", exchange -> handleRecoveryReset(exchange, dao));
            server.createContext("/api/messages/recent", exchange -> handleRead(exchange, (params, limit) -> dao.fetchRecent(limit), 10));
            server.createContext("/api/messages/search", exchange -> handleRead(exchange, (params, limit) -> dao.fetchByAuthor(params.getOrDefault("prefix", ""), limit), 15));
            server.createContext("/api/messages/my", exchange -> handleRead(exchange, (params, limit) -> dao.fetchByAuthor(params.getOrDefault("author", ""), limit), 5));
            server.createContext("/api/messages/send", exchange -> handleSend(exchange, dao));
            server.createContext("/api/messages/delete", exchange -> handleDeleteMessage(exchange, dao));
            server.createContext("/api/contacts", exchange -> handleContacts(exchange, dao));
            server.createContext("/api/users/search", exchange -> handleUserSearch(exchange, dao));
            server.createContext("/api/admin/master", exchange -> handleAdminMaster(exchange, dao));
            server.start();
            System.out.println("Chat server is listening on port " + port);
        } catch (Exception ex) {
            System.err.println("Failed to start server: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private static int resolvePort() {
        String portValue = System.getenv("PORT");
        if (portValue == null || portValue.isBlank()) {
            return DEFAULT_PORT;
        }
        try {
            return Integer.parseInt(portValue.trim());
        } catch (NumberFormatException ex) {
            return DEFAULT_PORT;
        }
    }

    private static void handleRead(HttpExchange exchange, MessageSupplier supplier, int defaultLimit) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendPlain(exchange, 405, "Only GET allowed");
            return;
        }
        Map<String, String> params = queryParams(exchange);
        int limit = requestedLimit(params, defaultLimit);
        try {
            List<Message> messages = supplier.get(params, limit);
            sendJson(exchange, messagesToJson(messages));
        } catch (Exception ex) {
            sendPlain(exchange, 500, "Unable to load messages");
        }
    }

    private static int requestedLimit(Map<String, String> params, int defaultLimit) {
        if (defaultLimit <= 0) {
            return 0;
        }
        String value = params.get("limit");
        if (value == null || value.isEmpty()) {
            return defaultLimit;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return defaultLimit;
        }
    }

    private static void handleLogin(HttpExchange exchange, ChatDao dao) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendPlain(exchange, 405, "Only POST allowed");
            return;
        }

        LoginPayload payload;
        try {
            payload = GSON.fromJson(readBody(exchange), LoginPayload.class);
        } catch (Exception ex) {
            sendPlain(exchange, 400, "Malformed payload");
            return;
        }

        if (payload == null || isBlank(payload.handle) || isBlank(payload.password)) {
            sendPlain(exchange, 400, "Both handle and password are required");
            return;
        }

        String handle = payload.handle.trim();
        String password = payload.password.trim();
        try {
            boolean exists = dao.userExists(handle);
            if (!exists) {
                if (isBlank(payload.recoveryQuestion) || isBlank(payload.recoveryAnswer)) {
                    sendPlain(exchange, 400, "Recovery question and answer are required for new account");
                    return;
                }
                dao.registerUser(handle, password, payload.recoveryQuestion.trim(), payload.recoveryAnswer.trim());
            } else if (!dao.validateLogin(handle, password)) {
                sendPlain(exchange, 401, "Invalid username or password");
                return;
            }

            boolean admin = dao.isAdmin(handle);
            JsonObject response = new JsonObject();
            response.addProperty("message", "Signed in successfully");
            response.addProperty("admin", admin);
            if (admin) {
                String token = UUID.randomUUID().toString();
                ADMIN_TOKENS.put(token, handle);
                response.addProperty("adminToken", token);
            }
            sendJsonObject(exchange, response);
        } catch (Exception ex) {
            sendPlain(exchange, 500, "Login failed");
        }
    }

    private static void handleRecoveryQuestion(HttpExchange exchange, ChatDao dao) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendPlain(exchange, 405, "Only POST allowed");
            return;
        }

        RecoveryQuestionPayload payload;
        try {
            payload = GSON.fromJson(readBody(exchange), RecoveryQuestionPayload.class);
        } catch (Exception ex) {
            sendPlain(exchange, 400, "Malformed payload");
            return;
        }

        if (payload == null || isBlank(payload.handle)) {
            sendPlain(exchange, 400, "Username is required");
            return;
        }

        try {
            String question = dao.recoveryQuestion(payload.handle.trim());
            if (question == null) {
                sendPlain(exchange, 404, "User not found");
                return;
            }
            JsonObject response = new JsonObject();
            response.addProperty("question", question);
            sendJsonObject(exchange, response);
        } catch (Exception ex) {
            sendPlain(exchange, 500, "Unable to fetch recovery question");
        }
    }

    private static void handleRecoveryReset(HttpExchange exchange, ChatDao dao) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendPlain(exchange, 405, "Only POST allowed");
            return;
        }

        RecoveryResetPayload payload;
        try {
            payload = GSON.fromJson(readBody(exchange), RecoveryResetPayload.class);
        } catch (Exception ex) {
            sendPlain(exchange, 400, "Malformed payload");
            return;
        }

        if (payload == null || isBlank(payload.handle) || isBlank(payload.answer) || isBlank(payload.newPassword)) {
            sendPlain(exchange, 400, "Username, answer, and new password are required");
            return;
        }

        try {
            boolean changed = dao.resetPassword(payload.handle.trim(), payload.answer.trim(), payload.newPassword.trim());
            if (!changed) {
                sendPlain(exchange, 401, "Invalid recovery answer or user not found");
                return;
            }
            JsonObject response = new JsonObject();
            response.addProperty("message", "Password reset successful");
            sendJsonObject(exchange, response);
        } catch (Exception ex) {
            sendPlain(exchange, 500, "Unable to reset password");
        }
    }

    private static void handleSend(HttpExchange exchange, ChatDao dao) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendPlain(exchange, 405, "Only POST allowed");
            return;
        }
        String body = readBody(exchange);
        SendPayload payload;
        try {
            payload = GSON.fromJson(body, SendPayload.class);
        } catch (Exception ex) {
            sendPlain(exchange, 400, "Malformed payload");
            return;
        }
        if (payload == null || isBlank(payload.author) || isBlank(payload.password) || isBlank(payload.text)) {
            sendPlain(exchange, 400, "Author, password, and text are required");
            return;
        }
        try {
            String author = payload.author.trim();
            if (!dao.validateLogin(author, payload.password.trim())) {
                sendPlain(exchange, 401, "Invalid username or password");
                return;
            }
            dao.storeMessage(author, payload.text.trim());
            List<Message> latest = dao.fetchByAuthor(author, 1);
            sendJson(exchange, messagesToJson(latest));
        } catch (Exception ex) {
            sendPlain(exchange, 500, "Unable to store message");
        }
    }

    private static void handleDeleteMessage(HttpExchange exchange, ChatDao dao) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendPlain(exchange, 405, "Only POST allowed");
            return;
        }

        DeletePayload payload;
        try {
            payload = GSON.fromJson(readBody(exchange), DeletePayload.class);
        } catch (Exception ex) {
            sendPlain(exchange, 400, "Malformed payload");
            return;
        }

        if (payload == null || payload.messageId == null || isBlank(payload.handle)) {
            sendPlain(exchange, 400, "messageId and handle are required");
            return;
        }

        try {
            boolean deleted = dao.deleteMessage(payload.messageId, payload.handle.trim());
            if (!deleted) {
                sendPlain(exchange, 404, "Message not found");
                return;
            }
            JsonObject response = new JsonObject();
            response.addProperty("deleted", true);
            sendJsonObject(exchange, response);
        } catch (Exception ex) {
            sendPlain(exchange, 500, "Unable to delete message");
        }
    }

    private static void handleContacts(HttpExchange exchange, ChatDao dao) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendPlain(exchange, 405, "Only GET allowed");
            return;
        }

        Map<String, String> params = queryParams(exchange);
        String handle = params.getOrDefault("handle", "").trim();
        if (handle.isEmpty()) {
            sendPlain(exchange, 400, "handle is required");
            return;
        }

        try {
            List<String> contacts = dao.fetchContacts(handle);
            JsonArray out = new JsonArray();
            for (String contact : contacts) {
                out.add(contact);
            }
            sendJson(exchange, out);
        } catch (Exception ex) {
            sendPlain(exchange, 500, "Unable to load contacts");
        }
    }

    private static void handleUserSearch(HttpExchange exchange, ChatDao dao) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendPlain(exchange, 405, "Only GET allowed");
            return;
        }

        Map<String, String> params = queryParams(exchange);
        String prefix = params.getOrDefault("handle", "").trim();
        if (prefix.isEmpty()) {
            sendPlain(exchange, 400, "handle is required");
            return;
        }

        try {
            List<String> users = dao.findUsersByPrefix(prefix, 10);
            JsonObject response = new JsonObject();
            response.addProperty("exists", users.stream().anyMatch(u -> u.equals(prefix)));
            sendJsonObject(exchange, response);
        } catch (Exception ex) {
            sendPlain(exchange, 500, "Unable to search users");
        }
    }

    private static void handleAdminMaster(HttpExchange exchange, ChatDao dao) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendPlain(exchange, 405, "Only GET allowed");
            return;
        }

        Map<String, String> params = queryParams(exchange);
        String token = params.getOrDefault("token", "").trim();
        if (token.isEmpty() || !ADMIN_TOKENS.containsKey(token)) {
            sendPlain(exchange, 401, "Unauthorized");
            return;
        }

        int limit = requestedLimit(params, 300);
        try {
            JsonObject response = new JsonObject();

            JsonArray usersJson = new JsonArray();
            for (String user : dao.fetchAllUsers(limit)) {
                usersJson.add(user);
            }

            JsonArray messagesJson = messagesToJson(dao.fetchAllMessages(limit));
            response.add("users", usersJson);
            response.add("messages", messagesJson);
            sendJsonObject(exchange, response);
        } catch (Exception ex) {
            sendPlain(exchange, 500, "Unable to load admin data");
        }
    }

    private static Map<String, String> queryParams(HttpExchange exchange) {
        Map<String, String> params = new HashMap<>();
        String query = exchange.getRequestURI().getRawQuery();
        if (query == null || query.isEmpty()) {
            return params;
        }
        for (String part : query.split("&")) {
            int idx = part.indexOf('=');
            if (idx == -1) {
                continue;
            }
            String name = decode(part.substring(0, idx));
            String value = decode(part.substring(idx + 1));
            params.put(name, value);
        }
        return params;
    }

    private static String decode(String encoded) {
        return URLDecoder.decode(encoded, StandardCharsets.UTF_8);
    }

    private static JsonArray messagesToJson(List<Message> messages) {
        JsonArray array = new JsonArray();
        for (Message message : messages) {
            JsonObject json = new JsonObject();
            json.addProperty("id", message.getId());
            json.addProperty("author", message.getAuthor());
            json.addProperty("text", message.getText());
            json.addProperty("timestamp", message.getTimestamp().toString());
            array.add(json);
        }
        return array;
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody(); ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            is.transferTo(buffer);
            return buffer.toString(StandardCharsets.UTF_8);
        }
    }

    private static void sendJson(HttpExchange exchange, JsonArray body) throws IOException {
        byte[] bytes = GSON.toJson(body).getBytes(StandardCharsets.UTF_8);
        sendResponse(exchange, 200, "application/json; charset=UTF-8", bytes);
    }

    private static void sendJsonObject(HttpExchange exchange, JsonObject body) throws IOException {
        byte[] bytes = GSON.toJson(body).getBytes(StandardCharsets.UTF_8);
        sendResponse(exchange, 200, "application/json; charset=UTF-8", bytes);
    }

    private static void sendPlain(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        sendResponse(exchange, status, "text/plain; charset=UTF-8", bytes);
    }

    private static void sendResponse(HttpExchange exchange, int status, String contentType, byte[] bytes) throws IOException {
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", contentType);
        headers.set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static void handleStatic(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendPlain(exchange, 405, "Only GET allowed");
            return;
        }

        String path = exchange.getRequestURI().getPath();
        if (path == null || path.isBlank() || "/".equals(path)) {
            path = "/login.html";
        }

        if (path.contains("..")) {
            sendPlain(exchange, 400, "Invalid path");
            return;
        }

        String resourcePath = "/public" + path;
        try (InputStream resource = ChatServer.class.getResourceAsStream(resourcePath)) {
            if (resource == null) {
                sendPlain(exchange, 404, "Not found");
                return;
            }
            byte[] bytes = resource.readAllBytes();
            sendResponse(exchange, 200, contentType(path), bytes);
        }
    }

    private static String contentType(String path) {
        if (path.endsWith(".html")) {
            return "text/html; charset=UTF-8";
        }
        if (path.endsWith(".css")) {
            return "text/css; charset=UTF-8";
        }
        if (path.endsWith(".js")) {
            return "application/javascript; charset=UTF-8";
        }
        if (path.endsWith(".json")) {
            return "application/json; charset=UTF-8";
        }
        return "text/plain; charset=UTF-8";
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    @FunctionalInterface
    private interface MessageSupplier {
        List<Message> get(Map<String, String> params, int limit) throws Exception;
    }

    private static final class SendPayload {
        String author;
        String password;
        String text;
    }

    private static final class LoginPayload {
        String handle;
        String password;
        String recoveryQuestion;
        String recoveryAnswer;
    }

    private static final class RecoveryQuestionPayload {
        String handle;
    }

    private static final class RecoveryResetPayload {
        String handle;
        String answer;
        String newPassword;
    }

    private static final class DeletePayload {
        Long messageId;
        String handle;
    }
}