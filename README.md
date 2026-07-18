# E-Bogcha

Enterprise management system for educational institutions. Centralizes student enrollment, CRM, communication, and administrative operations into a single platform.

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
├── scripts/                  # Utility and automation scripts
├── .github/
│   └── workflows/            # CI/CD pipelines
├── .gitignore
├── LICENSE
└── README.md
```

## Development Workflow

1. Clone the repository
2. Copy `.env.example` to `.env` and configure
3. Run `docker-compose up -d` to start services
4. Access the application at `http://localhost:3000`

```bash
git clone https://github.com/istam777/e-bogcha.git
cd e-bogcha
docker-compose up -d
```

## Branch Strategy

| Branch     | Purpose                        | Merges Into |
|------------|--------------------------------|-------------|
| `main`     | Production-ready code          | —           |
| `develop`  | Integration branch             | `main`      |
| `feature/*`| New features                   | `develop`   |
| `bugfix/*` | Bug fixes                      | `develop`   |
| `hotfix/*` | Critical production fixes      | `main`      |
