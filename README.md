# FinanceApp — Full-Stack Skeleton

React + Spring Boot + MongoDB with JWT Authentication.

## Project Structure

```
Finance - App/
├── backend/     ← Spring Boot 3.2 (Java 17+)
└── frontend/    ← React 18 + Vite
```

## Prerequisites

| Tool | Version | Notes |
|------|---------|-------|
| Java | 17+ | Required for Spring Boot 3.x |
| Node.js | 18+ | For React frontend |
| MongoDB | 6+ | Must be running on localhost:27017 |

> **No global Maven needed** — `mvnw.cmd` auto-downloads Maven 3.9.6 on first run.

## Start MongoDB (Windows)

```powershell
# If installed as a service:
net start MongoDB

# Or open MongoDB Compass and connect to mongodb://localhost:27017
```

## Start Backend

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

Backend runs on **http://localhost:8080**

## Start Frontend

```powershell
cd frontend
npm run dev
```

Frontend runs on **http://localhost:5173**

## REST API

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| POST | `/api/auth/register` | Public | Register, returns JWT |
| POST | `/api/auth/login` | Public | Login, returns JWT |
| GET | `/api/dashboard` | Bearer JWT | Protected dashboard data |

## Auth Flow

1. Register at `/register` → JWT stored in localStorage → redirect to Dashboard
2. Every `/api/*` request automatically includes `Authorization: Bearer <token>`
3. `/dashboard` is protected — unauthenticated visitors are redirected to `/login`
4. JWT expires after **24 hours**

## MongoDB Collections

| Collection | Description |
|------------|-------------|
| `users` | Stores username (unique), email (unique), bcrypt-hashed password |

Database name: `finance_app`

## Configuration

Edit `backend/src/main/resources/application.properties`:

```properties
spring.data.mongodb.uri=mongodb://localhost:27017/finance_app
jwt.secret=FinanceApp2024SecretKeyForJWTAuthenticationVeryLongAndSecureKey12345
jwt.expiration=86400000   # 24h in ms
server.port=8080
```
