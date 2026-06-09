# WhatsApp Assistant

A Spring Boot application that turns WhatsApp into a personal AI assistant. Connect your WhatsApp account from a web dashboard, then use natural language messages to chat with Gemini AI, manage your Google Calendar, and read or send emails through Gmail — all without leaving WhatsApp.

---

## Table of Contents

- [Features](#features)
- [Tech Stack](#tech-stack)
- [Project Structure](#project-structure)
- [How It Works](#how-it-works)
- [Environment Variables](#environment-variables)
- [Local Development](#local-development)
- [Deployment](#deployment)
- [Database Schema](#database-schema)
- [Command Reference](#command-reference)
- [Key Source Files](#key-source-files)
- [Security Notes](#security-notes)
- [Troubleshooting](#troubleshooting)

---

## Features

- Receive and process WhatsApp messages via Meta WhatsApp Cloud API webhook
- Dashboard-based account linking with one-time `WA-XXXXXX` codes
- AI chat powered by Gemini 2.0 Flash with per-user conversation history
- **Gmail** (via Composio)
  - Read recent or unread emails
  - Summarize emails
  - Draft and send emails with `YES` confirmation step
- **Google Calendar** (via Composio)
  - Create, list, and delete events using natural language
- PostgreSQL persistence via Supabase
- Render-ready with full environment variable configuration

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.3.x |
| Web | Spring MVC |
| Persistence | Spring Data JPA, PostgreSQL (Supabase) |
| Templating | Thymeleaf |
| AI | Google Gemini API |
| Messaging | Meta WhatsApp Cloud API |
| Integrations | Composio (Gmail, Google Calendar) |
| Deployment | Render, Docker |

---

## Project Structure

```
src/main/java/com/debasish/whatsappassistant
├── controller
│   ├── PageController.java
│   ├── WhatsAppWebhookController.java
│   ├── WhatsappConnectionController.java
│   └── ComposioController.java
├── service
│   ├── WhatsAppService.java
│   ├── GeminiService.java
│   ├── WhatsappConnectionService.java
│   ├── ComposioService.java
│   ├── MessageService.java
│   └── UserService.java
├── repository
│   ├── UserRepository.java
│   ├── MessageRepository.java
│   ├── WhatsappConnectionRepository.java
│   └── ComposioConnectionRepository.java
├── entity
│   ├── User.java
│   ├── Message.java
│   ├── WhatsappConnection.java
│   └── ComposioConnection.java
└── WhatsAppAssistantApplication.java

src/main/resources
├── application.properties
├── templates
│   ├── index.html
│   ├── login.html
│   ├── dashboard.html
│   └── settings.html
└── static
    ├── css/style.css
    └── js/app.js
```

---

## How It Works

### WhatsApp Account Linking

1. Open the dashboard and copy the generated `WA-XXXXXX` code.
2. Send that code as a WhatsApp message to your bot number.
3. The webhook detects the `WA-` prefix and calls `WhatsappConnectionService` to mark the account as connected.
4. The bot replies with `WhatsApp connected successfully.`
5. The dashboard card updates automatically via polling.

### Normal AI Chat

1. User sends any message not matching a Gmail or Calendar command.
2. Webhook loads recent filtered conversation history for that phone number.
3. `GeminiService` sends the history and message to Gemini and gets a reply.
4. The reply is saved to the `messages` table and sent back via WhatsApp.

### Sending an Email

1. User sends a message like:
   ```
   send mail to someone@example.com saying happy birthday
   ```
2. Gemini drafts a subject and body, which the bot shows to the user.
3. User replies `YES` to confirm (or anything else to cancel).
4. `ComposioService` executes `GMAIL_SEND_EMAIL`.
5. Bot confirms: `Done. Email sent.`

### Reading or Summarizing Emails

`ComposioService` calls `GMAIL_FETCH_EMAILS` with these parameters:

| Parameter | Value |
|---|---|
| `user_id` | `me` |
| `max_results` | `3` |
| `verbose` | `true` |
| `include_payload` | `true` |
| `label_ids` | `INBOX` or `INBOX,UNREAD` |

### Google Calendar

Incoming messages are routed to one of three Composio tools:

| Intent | Tool |
|---|---|
| Create event | `GOOGLECALENDAR_QUICK_ADD` |
| List events | `GOOGLECALENDAR_EVENTS_LIST` |
| Delete event | `GOOGLECALENDAR_DELETE_EVENT` |

---

## Environment Variables

Set these in your local environment or in the Render dashboard:

```
WHATSAPP_ACCESS_TOKEN=
WHATSAPP_PHONE_NUMBER_ID=
GEMINI_API_KEY=
COMPOSIO_API_KEY=
COMPOSIO_GMAIL_AUTH_CONFIG_ID=
COMPOSIO_GOOGLE_CALENDAR_AUTH_CONFIG_ID=
DATABASE_URL=
DATABASE_USERNAME=
DATABASE_PASSWORD=
PORT=8080
```

These are already wired in `application.properties`:

```properties
server.port=${PORT:8080}
whatsapp.access-token=${WHATSAPP_ACCESS_TOKEN}
whatsapp.phone-number-id=${WHATSAPP_PHONE_NUMBER_ID}
gemini.api-key=${GEMINI_API_KEY}
composio.api-key=${COMPOSIO_API_KEY}
composio.gmail-auth-config-id=${COMPOSIO_GMAIL_AUTH_CONFIG_ID}
composio.google-calendar-auth-config-id=${COMPOSIO_GOOGLE_CALENDAR_AUTH_CONFIG_ID}
spring.datasource.url=${DATABASE_URL}
spring.datasource.username=${DATABASE_USERNAME}
spring.datasource.password=${DATABASE_PASSWORD}
```

---

## Local Development

### 1. Run the application

**Windows:**
```powershell
.\mvnw.cmd spring-boot:run
```

**Linux / macOS:**
```bash
./mvnw spring-boot:run
```

The app starts at `http://localhost:8080`.

### 2. Expose the webhook with ngrok

```bash
ngrok http 8080
```

Use the generated HTTPS URL as your Meta webhook callback URL:
```
https://YOUR-NGROK-URL/webhook
```

In the Meta developer console:
- **Verify token:** `whatsappassistant123`
- **Subscribed field:** `messages`

---

## Deployment

### Render

**Build command:**
```bash
./mvnw clean package -DskipTests
```

**Start command:**
```bash
java -jar target/whatsapp-assistant-0.0.1-SNAPSHOT.jar
```

After deploying, update the Meta webhook callback URL to:
```
https://YOUR-RENDER-DOMAIN.onrender.com/webhook
```

Also update Composio's allowed redirect/callback URLs to include your Render domain.

### Docker

```dockerfile
FROM eclipse-temurin:21-jdk

WORKDIR /app

COPY . .

RUN chmod +x mvnw
RUN ./mvnw clean package -DskipTests

CMD ["java", "-jar", "target/whatsapp-assistant-0.0.1-SNAPSHOT.jar"]
```

Build and run:
```bash
docker build -t whatsapp-assistant .
docker run -p 8080:8080 --env-file .env whatsapp-assistant
```

---

## Database Schema

### `users`
Stores WhatsApp senders.

| Column | Type |
|---|---|
| `phone_number` | varchar (PK) |
| `name` | varchar |

### `messages`
Stores conversation history.

| Column | Type |
|---|---|
| `id` | bigint (PK) |
| `phone_number` | varchar |
| `user_message` | text |
| `ai_reply` | text |
| `created_at` | timestamp |

### `whatsapp_connections`
Tracks account linking state.

| Column | Type |
|---|---|
| `id` | bigint (PK) |
| `email` | varchar |
| `phone_number` | varchar |
| `link_code` | varchar |
| `is_connected` | boolean |

### `composio_connections`
Stores connected Gmail and Google Calendar accounts.

| Column | Type |
|---|---|
| `id` | bigint (PK) |
| `email` | varchar |
| `toolkit` | varchar |
| `connected_account_id` | varchar |
| `status` | varchar |
| `updated_at` | timestamp |

### `app_users`
Managed from the dashboard via Supabase JS.

| Column | Type |
|---|---|
| `email` | varchar (PK) |
| `name` | varchar |

---

## Command Reference

### Normal Chat
```
Hi
What can you do?
```

### Gmail
```
Summarize my recent mail
Summarize my first recent mail
Read my unread emails
Show latest email
Send mail to someone@example.com saying happy birthday
YES
NO
```

### Google Calendar
```
Set calendar meeting tomorrow at 5pm
Check my calendar
Create calendar event Team Meeting tomorrow at 3pm
Delete calendar event Project Review on 2026-06-10
```

---

## Key Source Files

| File | Responsibility |
|---|---|
| `WhatsAppWebhookController.java` | Receives webhook events, parses messages, routes to Gmail / Calendar / Gemini |
| `WhatsAppService.java` | Sends outbound messages via Meta WhatsApp Cloud API |
| `GeminiService.java` | Calls Gemini for chat replies and email draft generation |
| `ComposioService.java` | Manages Composio OAuth, executes Gmail and Calendar tools, formats responses |
| `WhatsappConnectionService.java` | Handles `WA-XXXXXX` code verification and account linking |
| `dashboard.html` | Frontend for connecting WhatsApp, Gmail, and Google Calendar |

---

## Security Notes

- Never commit API keys or access tokens to version control.
- Store all secrets in environment variables.
- Rotate any token that was accidentally exposed.
- Gmail send flow requires explicit `YES` confirmation before dispatching.
- `WA-XXXXXX` codes are processed before Gemini, so they are never stored as chat messages.

---

## Troubleshooting

### WhatsApp messages are not reaching the app

- Confirm ngrok is running and the tunnel is active.
- Check that the Meta callback URL ends with `/webhook`.
- Verify the token matches `whatsappassistant123`.
- Confirm the `messages` webhook field is subscribed in Meta.
- Check the terminal for `===== WEBHOOK RECEIVED =====`.

### Gmail shows as unavailable

- Confirm Gmail is connected in the dashboard.
- Check that `COMPOSIO_API_KEY` and `COMPOSIO_GMAIL_AUTH_CONFIG_ID` are set.
- Verify the Composio OAuth app has Gmail read and send scopes.

### Calendar command produces the wrong result

Prefer explicit phrasing:
```
Create calendar event Team Meeting tomorrow at 5pm
Delete calendar event Team Meeting on 2026-06-10
```

### App does not start on Render

- Check that all environment variables are set in the Render dashboard.
- Confirm `DATABASE_URL`, `DATABASE_USERNAME`, and `DATABASE_PASSWORD` are correct.
- Ensure the build command uses `./mvnw` (the Maven wrapper), not a globally installed Maven.