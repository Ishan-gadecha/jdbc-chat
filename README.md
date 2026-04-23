# ShipLink: The Ultimate 100-Step Masterclass

Welcome! This document is designed to take you from a complete beginner to understanding exactly how ShipLink was built from the ground up. Whether you are presenting this project or trying to learn how full-stack applications are made, these 100 steps cover every language, trick, and API used in the codebase.

---

## Phase 1: The Foundation & Architecture (Steps 1 - 10)

**1. The Big Picture:** ShipLink is a "Full-Stack" web application. This means it has a **Frontend** (what you see) and a **Backend** (the brain on the server).
**2. Languages Used:** We built the frontend purely with **HTML, CSS, and Vanilla JavaScript**. We didn't use heavy frameworks like React. The backend is written entirely in **Java**.
**3. The Client-Server Model:** Your web browser (Chrome/Safari) is the "Client". It talks over the internet to a central "Server" running our Java code.
**4. The Project Manager (Maven):** Java projects use a tool called Maven. The `pom.xml` file is the blueprint. It downloads required third-party tools automatically.
**5. Dependencies:** In `pom.xml`, we specifically imported `gson` (for handling data), `javax.servlet` (for web traffic), and `dev.samstevens.totp` (for Two-Factor Authentication).
**6. The Web Server (Tomcat):** We run this app on **Apache Tomcat**. Tomcat's job is to listen for internet traffic on port 8080 and pass it to our Java code.
**7. Folder Structure:** Our code lives in `src/main/`. The frontend files live in `src/main/webapp`, and the backend files live in `src/main/java`.
**8. No SQL Database:** As a neat trick, ShipLink does **not** use a MySQL database. It uses "In-Memory Storage". This makes it lightning fast but means data is wiped when the server restarts.
**9. RESTful API Architecture:** The frontend and backend communicate using "REST APIs" (urls like `/api/login` and `/api/messages/send`).
**10. JSON Formatting:** When the frontend and backend talk, they send data packed in **JSON** (JavaScript Object Notation), which looks like a simple text dictionary: `{"handle": "Alice", "text": "Hello"}`.

---

## Phase 2: The Data Vault (Java Backend) (Steps 11 - 25)

**11. The Vault (`ChatStore.java`):** This file is the brain of the app. It holds all user accounts and messages.
**12. The Singleton Pattern Trick:** `ChatStore` uses the "Singleton" design pattern (`private static final ChatStore INSTANCE`). This ensures there is only ever *one* vault in memory, so all users access the exact same data.
**13. In-Memory Data Structures:** To store users, we use a `ConcurrentHashMap`. This is a Java trick that creates a hyper-fast dictionary while preventing crashes if two users try to register at the exact same millisecond.
**14. The Account Class:** Inside `ChatStore`, there is a private `Account` class that stores usernames, passwords, and security answers.
**15. The MessageRecord Class:** Every chat message is saved as a `MessageRecord` containing the sender, receiver, timestamp, and text.
**16. Thread Safety:** Because many users send messages at once, critical methods like `sendMessage()` are marked `synchronized`. This forces Java to handle them one at a time, preventing data corruption.
**17. Auto-Registration Trick:** In `login()`, if the system doesn't recognize your username, it simply creates the account instantly instead of showing an error. It's a frictionless user experience!
**18. Password Recovery:** The store securely holds a recovery question and answer, allowing users to hit a `/api/recovery/reset` endpoint to change their password.
**19. Search Logic:** The `searchMessages()` method loops through the `messages` list backwards to find the most recent matches to your search prefix.
**20. The Two-Factor (TOTP) Setup:** We integrated Two-Factor Authentication (2FA) exclusively for the `admin` account. 
**21. Base32 Secret Key:** The admin account has a hardcoded Base32 secret key: `KNUGS4DMNFXGWQLENVUW4U3FMNZGK5CL`.
**22. Verifying TOTP:** When the admin logs in, Java uses the `dev.samstevens.totp` library to grab the current World Time, runs complex cryptography against the secret key, and checks if it matches the 6 digits the admin typed.
**23. Hidden Messages Trick:** When you "delete" a message, we don't actually erase it from the server. We add your username to a `hiddenBy` list. That way, it disappears for you, but the other person can still see it!
**24. Linking Contacts:** Whenever you message someone new, the `linkContacts()` method automatically adds them to your "Recent Contacts" list using a `LinkedHashSet` (to prevent duplicates).
**25. The Admin Snapshot:** The `getAdminSnapshot()` method allows a verified admin to pull down every user and message in the system for moderation.

---

## Phase 3: The Traffic Cop (`ChatServlet.java`) (Steps 26 - 40)

**26. What is a Servlet?** A Servlet is Java's way of catching web requests. `ChatServlet.java` extends `HttpServlet` to do exactly this.
**27. Routing:** Tomcat routes all traffic starting with `/api/` directly to this file.
**28. GET vs POST:** `doGet()` handles requests asking for data (like refreshing the chat). `doPost()` handles requests sending data (like logging in or sending a message).
**29. Reading the Envelope:** The `readBody()` method takes the raw incoming data stream from the browser and converts it into a readable String.
**30. The GSON Library:** We use Google's `GSON` library to magically convert that raw String into a usable Java Object (`Body`).
**31. XSS Prevention:** We explicitly use `new GsonBuilder().disableHtmlEscaping()` and sanitize inputs to prevent Hackers from sending malicious code (Cross-Site Scripting).
**32. The Switch Statement:** Inside `dispatchPost()`, a large `switch` statement looks at the endpoint URL (e.g., `login` or `messages/send`) and decides which code to run.
**33. Dispatching to the Vault:** If you hit `login`, the Servlet extracts your username and hands it to `ChatStore.login()`.
**34. Writing JSON Responses:** Once the Vault finishes its job, the `writeJson()` method packages the response back into JSON format and sends it over the internet to the browser.
**35. HTTP Status Codes:** If you forget a password, the Servlet uses `HttpServletResponse.SC_BAD_REQUEST` (Status 400) to tell the browser "You made an error."
**36. Session Management:** On successful login, the Servlet uses `request.getSession().setAttribute()` to remember you on the server side.
**37. Catching Errors:** We wrap everything in a `try-catch` block. If `ChatStore` throws an `IllegalArgumentException`, the Servlet gracefully catches it and sends the error text to the screen.
**38. The Normalizer:** `trimOrEmpty()` is a helper method that cleans up accidental spaces in usernames so " Alice " becomes "Alice".
**39. Limits and Pagination:** Endpoints accept a `limit` parameter so the server doesn't crash trying to send 1,000,000 messages at once.
**40. Page Redirection:** If a user tries to access the API directly in their browser bar, the `isPageRequest()` method intercepts them and redirects them to the actual HTML page.

---

## Phase 4: Frontend Foundation (HTML & CSS) (Steps 41 - 55)

**41. The UI Vibe:** The design uses a sleek, dark-mode aesthetic with vibrant neon accents, tailored in the `<style>` block of `chat.html`.
**42. CSS Variables:** At the top of the CSS, `:root` defines core fonts (`Sora`, `Inter`) and the background color (`#010409`) so everything stays consistent.
**43. Flexbox Layout:** We extensively use `display: flex;` to align items. This is what perfectly centers the login card on the screen.
**44. CSS Grid Layout:** The main app uses `display: grid; grid-template-columns: 360px 1fr;`. This splits the screen into a 360px wide sidebar and gives the rest of the screen (`1fr`) to the chat.
**45. Glassmorphism Trick:** The `.login-card` uses `background: rgba(255,255,255,0.02)` and `backdrop-filter: blur(18px)` to create a beautiful, modern frosted-glass effect.
**46. Linear Gradients:** Buttons don't just have one color; they use `background: linear-gradient(135deg, #00ff94, #00a3ff)` to make them pop.
**47. Micro-Animations:** When you click a button, the CSS `transform: scale(0.97)` shrinks it slightly to give tactile feedback.
**48. Message Bubbles:** `chat.html` uses `.message--mine` to push your messages to the right side (`align-self: flex-end`) and color them blue.
**49. Custom Scrollbars:** Standard scrollbars are ugly, so we overrode them using `::-webkit-scrollbar` to make them thin and semi-transparent.
**50. Media Queries:** This is the magic behind mobile responsiveness! `@media (max-width: 960px)` detects if you are on a phone.
**51. Hiding the Sidebar:** On mobile, we change the sidebar to `position: fixed; left: -100%;`. This completely hides it off the left side of the screen.
**52. The Hamburger Menu:** A `☰` button is added but hidden on desktop (`display: none;`). The media query makes it visible on mobile.
**53. The Backdrop Dimmer:** We added a `.sidebar-backdrop` div that covers the screen with a black tint when the sidebar slides out on mobile.
**54. CSS Transitions:** By adding `transition: left 0.3s ease;`, the sidebar smoothly slides in rather than instantly popping into existence.
**55. Z-Index Layering:** Overlays like the video call use high `z-index` numbers (e.g., `200`) to guarantee they float above everything else on the screen.

---

## Phase 5: The Frontend Brain (Vanilla JavaScript) (Steps 56 - 75)

**56. Finding Elements:** The JavaScript starts by using `document.getElementById()` to grab every button and text box on the page so it can control them.
**57. Session Storage Tracking:** When you log in, `sessionStorage.setItem('handle', ...)` saves your username in the browser tab's temporary memory.
**58. Route Protection:** At the top of `chat.html`, JS checks if you have a handle in memory. If you don't, it forcefully redirects you back to `login.html`.
**59. The Fetch API:** This is how JavaScript talks to the Java server. `fetch('api/messages/recent')` sends an invisible HTTP request behind the scenes.
**60. Async / Await:** Because internet requests take time, we use `async` and `await`. This tells the code to pause and wait for the server's reply before continuing.
**61. Listening to Clicks:** `addEventListener('submit', ...)` tells the code to run a specific function the moment you hit Enter or click Send.
**62. Dynamic DOM Manipulation:** When messages arrive, JS doesn't reload the page. It uses `document.createElement('div')` to build a new chat bubble in memory and injects it into the screen!
**63. Auto-Scrolling:** After injecting a message, `container.scrollTop = container.scrollHeight` forces the chat window to scroll down to the newest message automatically.
**64. The Polling Trick (Real-Time Chat):** How do messages appear instantly? We use `setInterval(refreshChatView, 1000)`. This forces the browser to silently ask the server "Any new messages?" every single second!
**65. Performance Optimization:** Polling every second wastes battery. We added `document.addEventListener('visibilitychange')`. If you switch to another browser tab, the polling pauses to save resources!
**66. Handling File Uploads:** There's an `<input type="file">` for sending pictures.
**67. The FileReader API:** When you pick an image, JS uses `FileReader().readAsDataURL()` to read the actual pixels of the image from your hard drive.
**68. Base64 Encoding:** The FileReader converts the image into a massive string of text (Base64) so it can be sent as text inside the JSON envelope.
**69. Rendering Media:** When retrieving messages, JS checks the MIME type (`msg.mediaMime`). If it starts with `image/`, it creates an `<img>` tag and sets the `src` to the massive Base64 text string, rendering the picture!
**70. Logout Logic:** Clicking logout clears the `sessionStorage` and redirects you to the login page, completely wiping your local access.
**71. Selecting Contacts:** Clicking a name in the sidebar updates the global `selectedContact` variable, changes the header text, and triggers a fresh message fetch.
**72. Mobile Sidebar Logic:** On mobile, clicking the Hamburger button adds an `.open` class to the sidebar, triggering the CSS slide-in animation.
**73. Auto-Close Sidebar:** If you click a contact on mobile, JS automatically removes the `.open` class, instantly hiding the sidebar so you can start typing.
**74. Search Functionality:** The search bar queries `/api/users/search`. If it finds someone, JS dynamically creates a giant "Add User" button on the screen.
**75. Preventing Default Behaviors:** Forms naturally try to reload the entire web page when submitted. We use `event.preventDefault()` to stop this, making it feel like a seamless modern app.

---

## Phase 6: The Advanced APIs & Magic Tricks (Steps 76 - 100)

**76. The Jitsi Meet API:** To add video calling, building WebRTC from scratch is too complex. Instead, we import the external API script from `meet.ffmuc.net`. We specifically chose this open community server instead of the official `meet.jit.si` because it allows 100% anonymous, frictionless video calls without forcing users to download an app or log into Google!
**77. The Invisible Call Overlay:** We created a massive `#callOverlay` div covering the whole screen, but kept it hidden (`display: none`).
**78. Launching the Call:** Clicking "📞 Call" changes the overlay to `display: flex`, revealing it to the user.
**79. The Deterministic Room Trick:** This is the smartest trick in the app. How do two people join the same video call without the server coordinating it? 
**80. Alphabetical Sorting:** The JavaScript takes your username (e.g., "Charlie") and your friend's username (e.g., "Alice"). It sorts them alphabetically (`['Alice', 'Charlie']`) and joins them into a room name: `ShipLink_Alice_Charlie`.
**81. Peer-to-Peer Magic:** Because the sorting is alphabetical, it doesn't matter who clicks the "Call" button first. Both browsers will independently calculate the exact same room name and join the exact same Jitsi server!
**82. Initializing Jitsi:** We initialize `new JitsiMeetExternalAPI()` inside our `jitsiContainer` div, passing it the room name and your username. Jitsi handles all the camera permissions and video streaming.
**83. Clean Call Termination:** Clicking "End Call" calls `jitsiApi.dispose()`. This shuts off your camera light and completely destroys the Jitsi instance so it doesn't run in the background.
**84. The Recovery Flow Trick:** In the login screen, clicking "Forgot Password" unhides a whole secondary form within the same `.login-container`.
**85. Security Verification:** To reset a password, you have to successfully answer the recovery question. Only the server knows the real answer, preventing hackers from bypassing it.
**86. Disabling Buttons:** When you click "Login", JS immediately does `submitBtn.disabled = true`. This prevents you from accidentally clicking it 5 times and crashing the server while waiting for a response.
**87. The Generation Counter:** In the chat, there is a `conversationGeneration` counter. If you switch contacts very rapidly, old slow internet requests might arrive late and show the wrong messages. The counter ensures only messages meant for the *current* contact are displayed!
**88. Timestamps:** Server timestamps are formatted using `Instant.now().toString()`.
**89. Human-Readable Time:** The frontend takes the ugly server timestamp and runs a custom `formatTimestamp()` JS function to convert it into pretty text like "Today at 5:30 PM".
**90. Dynamic Header:** The title of the page dynamically changes to `Chat as [YourName]` upon logging in.
**91. Browser Tab Routing:** If you hit refresh (`F5`), the browser detects it via `performance.getEntriesByType('navigation')` and actually logs you out to ensure a fresh clean state.
**92. Ghost Buttons:** We created a `.ghost` CSS class for secondary actions (like "Clear Media") that look transparent but gain a border to stand out slightly.
**93. Contact Validation:** The backend explicitly checks `accounts.containsKey()` before letting you send a message, ensuring you can't message someone who was deleted or doesn't exist.
**94. Custom Fonts:** We utilize `Sora` and `Inter` fonts, falling back to `system-ui` so that the app looks native whether you are on a Mac, Windows, or iOS device.
**95. Max Payload Size:** The frontend explicitly checks if an image is over 8MB (`file.size > MAX_MEDIA_SIZE`). If it is, it rejects it instantly, saving you from a long upload that the server would ultimately reject anyway.
**96. Graceful Failures:** If the Tomcat server crashes or you lose WiFi, the `catch (error)` blocks in JS intercept the failure and print "Connection error" directly onto the screen instead of crashing the UI.
**97. Scalability Limits:** The system passes a `limit=20` parameter to the API, meaning no matter how long your conversation history gets, it only downloads the 20 most recent bubbles to keep the app blazing fast.
**98. Modularity:** While small, the project cleanly separates concerns: UI logic in HTML/JS files, Routing in the Servlet, and Data Management in the Store.
**99. The "Admin" Master Key:** The `admin` account gets an `adminToken` upon login, giving it exclusive backend access to special endpoints like `/api/admin/master`.
**100. The Finished Product:** Through the seamless combination of a responsive CSS grid, event-driven JavaScript, and a multi-threaded Java Backend, ShipLink is a fully functioning, secure, modern chat application!
