# Bank of Fiji (BoF) — Online Banking System
**CS415 Advanced Software Engineering | Semester I, 2026**

---

## 🏗️ Architecture

```
bof-banking/
├── backend/    ← Spring Boot 3.4 (Java 21) — REST API on :8081
│   └── src/main/java/com/bof/banking/
│       ├── controller/   config/   dto/   exception/
│       ├── model/        repository/   service/
│       ├── scheduler/    security/     util/   mapper/
│       └── BankingApplication.java
└── frontend/   ← React 18 SPA — API URL from environment variables
    └── src/
        ├── context/      AuthContext.jsx
        ├── services/     api.js, authService.js, accountService.js, ...
        ├── components/   ProtectedRoute.jsx, Toast.jsx, ConfirmDialog.jsx, ...
        └── pages/        LoginPage, DashboardPage, AccountsPage, TransferPage,
                          LoansPage, StatementsPage, BillPaymentPages, ...
```

---

## 🚀 Quick Start

### Prerequisites
- Java 21+
- Maven 3.9+
- Node.js 18+
- PostgreSQL (default local dev: port **5432**)

---

### 1. Database

Create the database in PostgreSQL:
```sql
CREATE DATABASE bof_banking;
```

> Dev profile defaults to `localhost:5432`. Override with `SPRING_DATASOURCE_URL` if needed.

---

### 2. Backend Environment

```bash
cd backend
cp .env.template .env
```

Edit `.env` with your credentials:
```properties
SPRING_PROFILES_ACTIVE=dev
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/bof_banking
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=your_postgres_password

JWT_SECRET=your_long_random_secret
APP_CORS_ALLOWED_ORIGINS=http://localhost:3000

# Optional — email notifications
APP_MAIL_USERNAME=your_gmail@gmail.com
APP_MAIL_APP_PASSWORD=your_gmail_app_password
APP_NOTIFICATION_EMAIL_ENABLED=true
APP_NOTIFICATION_EMAIL_FROM=your_gmail@gmail.com
```

Production reference template: `backend/.env.production.template`

> ⚠️ Never commit `.env`. It is already in `.gitignore`.

---

### 3. Run Backend (Profiles)

Local development (uses `application-dev.properties`):

```bash
cd backend
mvn spring-boot:run
```

Production profile:

```bash
cd backend
SPRING_PROFILES_ACTIVE=prod mvn spring-boot:run
```

Required production environment variables:
- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `JWT_SECRET`
- `APP_CORS_ALLOWED_ORIGINS`

If any required value is missing in production, startup fails fast via `ProductionEnvironmentValidator`.

Backend starts at: **http://localhost:8081**

API docs (Swagger UI): http://localhost:8081/swagger-ui.html

---

### 4. Run Frontend

```bash
cd frontend
npm install
npm start
```

Frontend starts at: **http://localhost:3000**

Frontend API environment files:
- `frontend/.env.development` → `REACT_APP_API_URL=http://localhost:8081`
- `frontend/.env.production` → `REACT_APP_API_URL=http://168.144.46.191:8081`

---

## 🔑 Demo Accounts (auto-seeded on startup)

| Role     | Email                     | Password     |
|----------|---------------------------|--------------|
| Admin    | admin@bof.com.fj          | Admin@123    |
| Customer | john.doe@example.com      | Password@123 |

---

## 📡 Key API Endpoints

| Method | URL                        | Auth   | Description                     |
|--------|----------------------------|--------|---------------------------------|
| POST   | /api/auth/login            | Public | Login → returns JWT token       |
| POST   | /api/auth/register         | Public | Register new customer           |
| GET    | /api/auth/profile          | Bearer | Get current user profile        |
| GET    | /api/accounts              | Bearer | List user accounts              |
| POST   | /api/transfers             | Bearer | BoF transfer                    |
| GET    | /api/transactions          | Bearer | Transaction history             |
| GET    | /api/statements            | Bearer | Bank statements (PDF download)  |
| POST   | /api/bill-payments         | Bearer | Pay a bill                      |
| GET    | /api/scheduled-payments    | Bearer | Scheduled bill payments         |
| GET    | /api/loans                 | Bearer | Loan accounts                   |

Full API reference: **http://localhost:8081/swagger-ui.html**

---

## 🛠️ Tech Stack

| Layer     | Technology                                          |
|-----------|-----------------------------------------------------|
| Backend   | Java 21, Spring Boot 3.4, Spring Security 6         |
| Auth      | JWT (jjwt 0.12.6), BCrypt                           |
| Database  | PostgreSQL, Spring Data JPA / Hibernate              |
| Email     | Spring Mail — Gmail SMTP                            |
| PDF       | iText 5                                             |
| Frontend  | React 18, React Router 6, Axios                     |
| API Docs  | SpringDoc OpenAPI (Swagger UI)                      |
