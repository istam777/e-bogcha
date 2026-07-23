# E-Bog'cha Frontend

React + TypeScript frontend for the E-Bog'cha CRM system.

## Quick Start

```bash
npm install
npm run dev
```

Open http://localhost:5173 and enter a development actor UUID when prompted.

## Scripts

| Command | Description |
|---|---|
| `npm run dev` | Start development server |
| `npm run build` | Production build |
| `npm run typecheck` | TypeScript type check |
| `npm run lint` | ESLint |
| `npm run test -- --run` | Run tests |
| `npm run format` | Format code with Prettier |

## Environment

Copy `.env.example` to `.env.local` and configure:

```
VITE_API_BASE_URL=
VITE_DEV_PROXY_TARGET=http://localhost:8080
VITE_ACTOR_USER_ID=
```

## Architecture

Feature-oriented structure with strict dependency rules:
- `shared/` — reusable code (no imports from `features/`)
- `features/` — domain-specific modules
- `app/` — routing, providers, layout

See `docs/07_UI_UX/FE001_FRONTEND.md` for full documentation.
