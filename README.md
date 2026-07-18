# E-Bogcha

Enterprise management system for educational institutions (kindergartens, schools, and learning centers). Centralizes student enrollment, CRM, communication, and administrative operations into a single platform.

## Technology Stack

| Layer         | Technology                          |
|---------------|-------------------------------------|
| Frontend      | React 18+, TypeScript, Tailwind CSS |
| Backend       | Spring Boot 3, Java 21              |
| Database      | PostgreSQL 16                       |
| Cache         | Redis 7                             |
| Search        | Elasticsearch 8                     |
| Auth          | Spring Security + JWT               |
| Build         | Maven, Vite, Docker                 |
| CI/CD         | GitHub Actions                      |
| Infra         | Docker Compose, Nginx               |

## Project Structure

```
e-bogcha/
├── backend/                  # Spring Boot REST API
├── frontend/                 # React SPA
├── database/                 # Migrations, seeds, SQL scripts
├── docs/                     # Project documentation
│   ├── 01_Project_Foundation
│   ├── 02_System_Architecture
│   ├── 03_Database
│   ├── 04_CRM
│   ├── 05_Admissions
│   ├── 06_API
│   ├── 07_UI_UX
│   ├── 08_Development
│   └── 09_Deployment
├── branding/                 # Logos, colors, design assets
├── infrastructure/           # DevOps configuration
│   ├── docker/
│   ├── nginx/
│   └── scripts/
├── scripts/                  # Utility and automation scripts
├── .github/
│   └── workflows/            # CI/CD pipelines
├── .env.example
├── docker-compose.yml
├── LICENSE
└── README.md
```

## Development Workflow

1. Clone the repository
2. Copy `.env.example` to `.env` and configure
3. Run `docker-compose up -d` to start services
4. Access the application at `http://localhost:3000`

```bash
# Clone
git clone https://github.com/istam777/e-bogcha.git
cd e-bogcha

# Setup
cp .env.example .env
docker-compose up -d

# Development
# Backend: http://localhost:8080
# Frontend: http://localhost:3000
# Database: localhost:5432
```

## Branch Strategy

| Branch     | Purpose                        | Merges Into |
|------------|--------------------------------|-------------|
| `main`     | Production-ready code          | —           |
| `develop`  | Integration branch             | `main`      |
| `feature/*`| New features                   | `develop`   |
| `bugfix/*` | Bug fixes                      | `develop`   |
| `hotfix/*` | Critical production fixes      | `main`      |
| `release/*`| Release preparation            | `main`      |

**Rules:**
- All changes go through Pull Requests
- Minimum 1 approval required
- CI must pass before merge
- `main` is always deployable
- Squash merge into `develop`, merge commit into `main`
