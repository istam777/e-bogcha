# 02 — System Architecture

## Overview

E-Bogcha follows a layered monolith architecture with clear separation of concerns, designed for horizontal scaling when needed.

```
┌─────────────────────────────────────────────────────┐
│                    CLIENT LAYER                      │
│         Web Browser / Mobile Browser                 │
└──────────────────────┬──────────────────────────────┘
                       │ HTTPS
┌──────────────────────▼──────────────────────────────┐
│                  NGINX REVERSE PROXY                 │
│            SSL Termination / Rate Limiting           │
└──────────┬───────────────────────────┬──────────────┘
           │ /api/*                    │ /*
┌──────────▼──────────┐    ┌──────────▼──────────────┐
│    BACKEND API      │    │    FRONTEND SPA          │
│   Spring Boot 3     │    │    React 18 + Vite       │
│   Port: 8080        │    │    Port: 3000            │
└──────────┬──────────┘    └─────────────────────────┘
           │
┌──────────▼──────────────────────────────────────────┐
│                 APPLICATION LAYER                    │
│  ┌──────────┐  ┌──────────┐  ┌───────────────────┐  │
│  │ Auth     │  │ Business │  │ Notification      │  │
│  │ Module   │  │ Modules  │  │ Service           │  │
│  └────┬─────┘  └────┬─────┘  └────────┬──────────┘  │
└───────┼──────────────┼────────────────┼──────────────┘
        │              │                │
┌───────▼──────────────▼────────────────▼──────────────┐
│                   DATA LAYER                         │
│  ┌──────────┐  ┌──────────┐  ┌───────────────────┐  │
│  │PostgreSQL│  │  Redis   │  │ Elasticsearch     │  │
│  │ Primary  │  │  Cache   │  │  Search           │  │
│  └──────────┘  └──────────┘  └───────────────────┘  │
└──────────────────────────────────────────────────────┘
```

## Core Modules

### Authentication & Authorization

- JWT-based stateless authentication
- Role-based access control (RBAC)
- Token refresh mechanism
- Session management via Redis

### CRM Module

- Lead management pipeline
- Contact and company profiles
- Activity tracking and notes
- Email integration

### Admissions Module

- Application intake forms
- Document upload and verification
- Approval workflow engine
- Status tracking and notifications

### Notification Module

- Email delivery (SMTP)
- SMS integration
- In-app notifications
- Scheduled reminders

## Data Flow

```
User Request
    │
    ▼
Nginx (SSL, Rate Limit, Static)
    │
    ├──► Frontend (Static Files)
    │
    └──► Backend API
            │
            ├──► Authentication Filter
            │       └──► JWT Validation
            │
            ├──► Controller Layer
            │
            ├──► Service Layer (Business Logic)
            │
            ├──► Repository Layer (Data Access)
            │
            └──► PostgreSQL / Redis
```

## Security Layers

| Layer | Responsibility |
|-------|----------------|
| Nginx | SSL termination, rate limiting, DDoS protection |
| API Gateway | Request routing, CORS, request validation |
| Auth Filter | JWT validation, session checks |
| RBAC | Role and permission enforcement |
| Service | Input validation, business rules |
| Repository | SQL injection prevention (parameterized queries) |

## Scalability Considerations

- **Horizontal:** Stateless backend allows multiple instances behind load balancer
- **Vertical:** PostgreSQL connection pooling via HikariCP
- **Cache:** Redis for session store, frequently accessed data, rate limiting
- **Search:** Elasticsearch offloads complex queries from PostgreSQL
- **Async:** Message queue for email/SMS delivery, background jobs

## Environment Architecture

| Environment | Purpose | Infrastructure |
|-------------|---------|----------------|
| Development | Local development | Docker Compose |
| Staging | Pre-production testing | Docker + Cloud VM |
| Production | Live system | Docker Swarm / Kubernetes |

## Directory Reference

- `infrastructure/docker/` — Dockerfiles and compose files
- `infrastructure/nginx/` — Nginx configuration
- `backend/` — Spring Boot application source
- `frontend/` — React application source
- `database/` — Migrations and seed data
