package com.example.chat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public final class ChatStore {
    private static final ChatStore INSTANCE = new ChatStore();

    private final Map<String, Account> accounts = new ConcurrentHashMap<>();
    private final Map<String, LinkedHashSet<String>> contacts = new ConcurrentHashMap<>();
    private final List<MessageRecord> messages = new CopyOnWriteArrayList<>();
    private final AtomicInteger nextMessageId = new AtomicInteger(1);
    private final Map<String, AdminSession> adminSessions = new ConcurrentHashMap<>();

    private ChatStore() {
        accounts.put("admin", new Account("admin", "admin", null, null, true));
        contacts.put("admin", new LinkedHashSet<>());
    }

    public static ChatStore getInstance() {
        return INSTANCE;
    }

    public synchronized LoginResult login(String handle, String password, String recoveryQuestion, String recoveryAnswer) {
        String normalizedHandle = normalize(handle);
        String normalizedPassword = normalize(password);
        String normalizedQuestion = normalize(recoveryQuestion);
        String normalizedAnswer = normalize(recoveryAnswer);

        if (normalizedHandle.isEmpty() || normalizedPassword.isEmpty()) {
            throw new IllegalArgumentException("Handle and password are required.");
        }

        Account existing = accounts.get(normalizedHandle);
        if (existing == null) {
            if (normalizedQuestion.isEmpty() || normalizedAnswer.isEmpty()) {
                throw new IllegalArgumentException("Recovery question and answer are required for a new account.");
            }
            accounts.put(normalizedHandle, new Account(normalizedHandle, normalizedPassword, normalizedQuestion, normalizedAnswer, false));
            contacts.putIfAbsent(normalizedHandle, new LinkedHashSet<>());
            return new LoginResult(true, "Account created for " + normalizedHandle + ".", false, null);
        }

        if (!existing.password.equals(normalizedPassword)) {
            throw new IllegalArgumentException("Invalid handle or password.");
        }

        if (existing.admin) {
            return new LoginResult(true, "Signed in as admin.", true, issueAdminToken());
        }

        return new LoginResult(true, "Signed in as " + normalizedHandle + ".", false, null);
    }

    public synchronized String getRecoveryQuestion(String handle) {
        Account account = requireAccount(handle);
        if (account.recoveryQuestion == null || account.recoveryQuestion.isBlank()) {
            throw new IllegalArgumentException("No recovery question is set for this account.");
        }
        return account.recoveryQuestion;
    }

    public synchronized String resetPassword(String handle, String answer, String newPassword) {
        Account account = requireAccount(handle);
        if (normalize(answer).isEmpty() || normalize(newPassword).isEmpty()) {
            throw new IllegalArgumentException("Answer and new password are required.");
        }
        if (!account.recoveryAnswer.equalsIgnoreCase(normalize(answer))) {
            throw new IllegalArgumentException("Recovery answer did not match.");
        }
        account.password = normalize(newPassword);
        return "Password reset for " + account.handle + ".";
    }

    public synchronized ChatMessage sendMessage(String author, String password, String recipient, String text, String mediaData, String mediaMime) {
        Account account = requireAccount(author);
        if (!account.password.equals(normalize(password))) {
            throw new IllegalArgumentException("Invalid handle or password.");
        }

        String normalizedText = normalize(text);
        if (normalizedText.isEmpty()) {
            throw new IllegalArgumentException("Message text is required.");
        }

        String normalizedRecipient = normalize(recipient);
        if (!normalizedRecipient.isEmpty() && !accounts.containsKey(normalizedRecipient)) {
            throw new IllegalArgumentException("Recipient does not exist.");
        }

        MessageRecord record = new MessageRecord(
                nextMessageId.getAndIncrement(),
                account.handle,
                normalizedRecipient,
                normalizedText,
                normalize(mediaData),
                normalize(mediaMime),
                Instant.now().toString());
        messages.add(record);

        if (!normalizedRecipient.isEmpty()) {
            linkContacts(account.handle, normalizedRecipient);
        }

        return toMessage(record);
    }

    public synchronized boolean deleteMessageForUser(int messageId, String handle) {
        String normalizedHandle = normalize(handle);
        if (normalizedHandle.isEmpty() || !accounts.containsKey(normalizedHandle)) {
            throw new IllegalArgumentException("Invalid handle.");
        }

        for (MessageRecord record : messages) {
            if (record.id == messageId) {
                record.hiddenBy.add(normalizedHandle);
                return true;
            }
        }

        throw new IllegalArgumentException("Message not found.");
    }

    public List<ChatMessage> getRecentMessages(int limit) {
        return collectMessages(null, null, limit, false);
    }

    public List<ChatMessage> getConversation(String currentUser, String chatWith, int limit) {
        return collectMessages(currentUser, chatWith, limit, true);
    }

    public List<ChatMessage> getMessagesByAuthor(String author, int limit) {
        String normalizedAuthor = normalize(author);
        if (normalizedAuthor.isEmpty()) {
            return Collections.emptyList();
        }

        List<ChatMessage> result = new ArrayList<>();
        for (int index = messages.size() - 1; index >= 0 && result.size() < Math.max(1, limit); index--) {
            MessageRecord record = messages.get(index);
            if (record.author.equalsIgnoreCase(normalizedAuthor) && !record.isHiddenFor(normalizedAuthor)) {
                result.add(toMessage(record));
            }
        }
        return result;
    }

    public List<ChatMessage> searchMessages(String prefix, int limit) {
        String normalizedPrefix = normalize(prefix).toLowerCase();
        if (normalizedPrefix.isEmpty()) {
            return Collections.emptyList();
        }

        List<ChatMessage> result = new ArrayList<>();
        for (int index = messages.size() - 1; index >= 0 && result.size() < Math.max(1, limit); index--) {
            MessageRecord record = messages.get(index);
            if (record.text.toLowerCase().startsWith(normalizedPrefix) || record.author.toLowerCase().startsWith(normalizedPrefix)) {
                result.add(toMessage(record));
            }
        }
        return result;
    }

    public boolean accountExists(String handle) {
        return accounts.containsKey(normalize(handle));
    }

    public List<String> searchUsers(String prefix) {
        String normalizedPrefix = normalize(prefix).toLowerCase();
        List<String> result = new ArrayList<>();
        for (String handle : accounts.keySet()) {
            if (!normalizedPrefix.isEmpty() && handle.toLowerCase().startsWith(normalizedPrefix)) {
                result.add(handle);
            }
        }
        Collections.sort(result);
        return result;
    }

    public synchronized void addContact(String ownerHandle, String contactHandle) {
        String normalizedOwner = normalize(ownerHandle);
        String normalizedContact = normalize(contactHandle);

        if (normalizedOwner.isEmpty() || normalizedContact.isEmpty()) {
            throw new IllegalArgumentException("Owner and contact are required.");
        }
        if (!accounts.containsKey(normalizedOwner) || !accounts.containsKey(normalizedContact)) {
            throw new IllegalArgumentException("Both users must exist.");
        }

        linkContacts(normalizedOwner, normalizedContact);
    }

    public List<String> listContacts(String handle) {
        LinkedHashSet<String> contactSet = contacts.get(normalize(handle));
        if (contactSet == null || contactSet.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(contactSet);
    }

    public synchronized AdminSnapshot getAdminSnapshot(String token, int limit) {
        cleanupExpiredAdminSessions();
        if (!adminSessions.containsKey(normalize(token))) {
            throw new IllegalArgumentException("Unauthorized.");
        }

        List<String> users = new ArrayList<>(accounts.keySet());
        Collections.sort(users);

        List<ChatMessage> recentMessages = new ArrayList<>();
        for (int index = messages.size() - 1; index >= 0 && recentMessages.size() < Math.max(1, limit); index--) {
            recentMessages.add(toMessage(messages.get(index)));
        }

        return new AdminSnapshot(users, recentMessages);
    }

    private List<ChatMessage> collectMessages(String currentUser, String chatWith, int limit, boolean useConversationFilter) {
        String normalizedCurrentUser = normalize(currentUser);
        String normalizedChatWith = normalize(chatWith);
        int safeLimit = Math.max(1, limit);
        List<ChatMessage> result = new ArrayList<>();

        for (int index = messages.size() - 1; index >= 0 && result.size() < safeLimit; index--) {
            MessageRecord record = messages.get(index);
            if (useConversationFilter) {
                if (normalizedCurrentUser.isEmpty() || normalizedChatWith.isEmpty()) {
                    continue;
                }
                boolean matchesConversation = (record.author.equalsIgnoreCase(normalizedCurrentUser) && record.recipient.equalsIgnoreCase(normalizedChatWith))
                        || (record.author.equalsIgnoreCase(normalizedChatWith) && record.recipient.equalsIgnoreCase(normalizedCurrentUser));
                if (!matchesConversation || record.isHiddenFor(normalizedCurrentUser)) {
                    continue;
                }
            }
            result.add(toMessage(record));
        }
        return result;
    }

    private void linkContacts(String first, String second) {
        contacts.computeIfAbsent(first, key -> new LinkedHashSet<>()).add(second);
        contacts.computeIfAbsent(second, key -> new LinkedHashSet<>()).add(first);
    }

    private Account requireAccount(String handle) {
        Account account = accounts.get(normalize(handle));
        if (account == null) {
            throw new IllegalArgumentException("Account not found.");
        }
        return account;
    }

    private String issueAdminToken() {
        cleanupExpiredAdminSessions();
        String token = UUID.randomUUID().toString();
        adminSessions.put(token, new AdminSession(System.currentTimeMillis() + 15L * 60L * 1000L));
        return token;
    }

    private void cleanupExpiredAdminSessions() {
        long now = System.currentTimeMillis();
        adminSessions.entrySet().removeIf(entry -> entry.getValue().expiresAt < now);
    }

    private ChatMessage toMessage(MessageRecord record) {
        return new ChatMessage(record.id, record.author, record.text, record.recipient, record.timestamp, record.mediaData, record.mediaMime);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    public static final class LoginResult {
        private final boolean success;
        private final String message;
        private final boolean admin;
        private final String adminToken;

        private LoginResult(boolean success, String message, boolean admin, String adminToken) {
            this.success = success;
            this.message = message;
            this.admin = admin;
            this.adminToken = adminToken;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public boolean isAdmin() {
            return admin;
        }

        public String getAdminToken() {
            return adminToken;
        }
    }

    public static final class AdminSnapshot {
        private final List<String> users;
        private final List<ChatMessage> messages;

        private AdminSnapshot(List<String> users, List<ChatMessage> messages) {
            this.users = users;
            this.messages = messages;
        }

        public List<String> getUsers() {
            return users;
        }

        public List<ChatMessage> getMessages() {
            return messages;
        }
    }

    private static final class Account {
        private final String handle;
        private String password;
        private final String recoveryQuestion;
        private final String recoveryAnswer;
        private final boolean admin;

        private Account(String handle, String password, String recoveryQuestion, String recoveryAnswer, boolean admin) {
            this.handle = handle;
            this.password = password;
            this.recoveryQuestion = recoveryQuestion == null ? "" : recoveryQuestion;
            this.recoveryAnswer = recoveryAnswer == null ? "" : recoveryAnswer;
            this.admin = admin;
        }
    }

    private static final class AdminSession {
        private final long expiresAt;

        private AdminSession(long expiresAt) {
            this.expiresAt = expiresAt;
        }
    }

    private static final class MessageRecord {
        private final int id;
        private final String author;
        private final String recipient;
        private final String text;
        private final String mediaData;
        private final String mediaMime;
        private final String timestamp;
        private final Set<String> hiddenBy = ConcurrentHashMap.newKeySet();

        private MessageRecord(int id, String author, String recipient, String text, String mediaData, String mediaMime, String timestamp) {
            this.id = id;
            this.author = author;
            this.recipient = recipient == null ? "" : recipient;
            this.text = text;
            this.mediaData = mediaData;
            this.mediaMime = mediaMime;
            this.timestamp = timestamp;
        }

        private boolean isHiddenFor(String handle) {
            return hiddenBy.contains(handle == null ? "" : handle.trim());
        }
    }
}