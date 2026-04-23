package com.example.chat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class ChatServlet extends HttpServlet {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private final ChatStore chatStore = ChatStore.getInstance();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (isPageRequest(request)) {
            response.sendRedirect(request.getContextPath() + "/chat.html");
            return;
        }

        dispatchGet(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (isPageRequest(request)) {
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return;
        }

        dispatchPost(request, response);
    }

    private void dispatchGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String endpoint = normalizeEndpoint(request);

        try {
            switch (endpoint) {
                case "messages/recent": {
                    int limit = parseLimit(request.getParameter("limit"), 10);
                    String currentUser = firstNonEmpty(request.getParameter("currentUser"), request.getParameter("author"));
                    String chatWith = trimOrEmpty(request.getParameter("chatWith"));
                    List<ChatMessage> messages = currentUser.isEmpty() || chatWith.isEmpty()
                            ? chatStore.getRecentMessages(limit)
                            : chatStore.getConversation(currentUser, chatWith, limit);
                    writeJson(response, messages);
                    return;
                }
                case "messages/search": {
                    String prefix = firstNonEmpty(request.getParameter("prefix"), request.getParameter("search"));
                    int limit = parseLimit(request.getParameter("limit"), 50);
                    writeJson(response, chatStore.searchMessages(prefix, limit));
                    return;
                }
                case "messages/my": {
                    String author = firstNonEmpty(request.getParameter("author"), request.getParameter("handle"), request.getParameter("currentUser"));
                    int limit = parseLimit(request.getParameter("limit"), 20);
                    writeJson(response, chatStore.getMessagesByAuthor(author, limit));
                    return;
                }
                case "contacts": {
                    writeJson(response, chatStore.listContacts(request.getParameter("handle")));
                    return;
                }
                case "users/search": {
                    String handle = trimOrEmpty(request.getParameter("handle"));
                    writeJson(response, new UserSearchResult(chatStore.accountExists(handle), chatStore.searchUsers(handle)));
                    return;
                }
                case "admin/master": {
                    String token = trimOrEmpty(request.getParameter("token"));
                    int limit = parseLimit(request.getParameter("limit"), 300);
                    writeJson(response, chatStore.getAdminSnapshot(token, limit));
                    return;
                }
                default:
                    sendText(response, HttpServletResponse.SC_NOT_FOUND, "Unknown endpoint.");
            }
        } catch (IllegalArgumentException ex) {
            sendText(response, HttpServletResponse.SC_BAD_REQUEST, ex.getMessage());
        }
    }

    private void dispatchPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String endpoint = normalizeEndpoint(request);

        try {
            switch (endpoint) {
                case "login": {
                    Body body = readBody(request);
                    ChatStore.LoginResult result = chatStore.login(body.string("handle"), body.string("password"), body.string("recoveryQuestion"), body.string("recoveryAnswer"), body.string("totpCode"));
                    request.getSession(true).setAttribute("handle", body.string("handle"));
                    writeJson(response, result);
                    return;
                }
                case "messages/send": {
                    Body body = readBody(request);
                    ChatMessage message = chatStore.sendMessage(
                            body.string("author"),
                            body.string("password"),
                            body.string("to"),
                            body.string("text"),
                            body.string("mediaData"),
                            body.string("mediaMime"));
                    writeJson(response, message);
                    return;
                }
                case "messages/delete": {
                    Body body = readBody(request);
                    chatStore.deleteMessageForUser(body.intValue("messageId"), body.string("handle"));
                    writeJson(response, new StatusMessage("Message deleted for this user."));
                    return;
                }
                case "contacts/add": {
                    Body body = readBody(request);
                    chatStore.addContact(body.string("ownerHandle"), body.string("contactHandle"));
                    writeJson(response, new StatusMessage("Contact added."));
                    return;
                }
                case "recovery/question": {
                    Body body = readBody(request);
                    writeJson(response, new RecoveryQuestionResult(chatStore.getRecoveryQuestion(body.string("handle"))));
                    return;
                }
                case "recovery/reset": {
                    Body body = readBody(request);
                    writeJson(response, new StatusMessage(chatStore.resetPassword(body.string("handle"), body.string("answer"), body.string("newPassword"))));
                    return;
                }
                default:
                    sendText(response, HttpServletResponse.SC_NOT_FOUND, "Unknown endpoint.");
            }
        } catch (IllegalArgumentException ex) {
            sendText(response, HttpServletResponse.SC_BAD_REQUEST, ex.getMessage());
        }
    }

    private boolean isPageRequest(HttpServletRequest request) {
        return "/chat".equals(request.getServletPath());
    }

    private String trimOrEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private String firstNonEmpty(String... values) {
        for (String value : values) {
            String trimmed = trimOrEmpty(value);
            if (!trimmed.isEmpty()) {
                return trimmed;
            }
        }
        return "";
    }

    private String normalizeEndpoint(HttpServletRequest request) {
        String pathInfo = request.getPathInfo();
        if (pathInfo == null || pathInfo.isBlank()) {
            return "";
        }
        return pathInfo.startsWith("/") ? pathInfo.substring(1) : pathInfo;
    }

    private int parseLimit(String value, int defaultValue) {
        try {
            int parsed = Integer.parseInt(trimOrEmpty(value));
            return parsed > 0 ? parsed : defaultValue;
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private Body readBody(HttpServletRequest request) throws IOException {
        StringBuilder builder = new StringBuilder();
        char[] buffer = new char[1024];
        int read;
        try (var reader = new java.io.InputStreamReader(request.getInputStream(), StandardCharsets.UTF_8)) {
            while ((read = reader.read(buffer)) != -1) {
                builder.append(buffer, 0, read);
            }
        }
        return Body.fromJson(builder.toString());
    }

    private void writeJson(HttpServletResponse response, Object payload) throws IOException {
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        try (PrintWriter writer = response.getWriter()) {
            writer.write(GSON.toJson(payload));
        }
    }

    private void sendText(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("text/plain;charset=UTF-8");
        try (PrintWriter writer = response.getWriter()) {
            writer.write(message);
        }
    }

    private static final class StatusMessage {
        private final String message;

        private StatusMessage(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }

    private static final class RecoveryQuestionResult {
        private final String question;

        private RecoveryQuestionResult(String question) {
            this.question = question;
        }

        public String getQuestion() {
            return question;
        }
    }

    private static final class UserSearchResult {
        private final boolean exists;
        private final List<String> matches;

        private UserSearchResult(boolean exists, List<String> matches) {
            this.exists = exists;
            this.matches = matches;
        }

        public boolean isExists() {
            return exists;
        }

        public List<String> getMatches() {
            return matches;
        }
    }

    private static final class Body {
        private final JsonObject jsonObject;

        private Body(JsonObject jsonObject) {
            this.jsonObject = jsonObject;
        }

        private static Body fromJson(String raw) {
            if (raw == null || raw.trim().isEmpty()) {
                return new Body(new JsonObject());
            }
            return new Body(JsonParser.parseString(raw).getAsJsonObject());
        }

        private String string(String key) {
            if (!jsonObject.has(key) || jsonObject.get(key).isJsonNull()) {
                return "";
            }
            return jsonObject.get(key).getAsString();
        }

        private int intValue(String key) {
            String value = string(key);
            if (value.isEmpty()) {
                throw new IllegalArgumentException("Missing required numeric field: " + key);
            }
            return Integer.parseInt(value);
        }
    }
}
