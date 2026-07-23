# E-Bog'cha Frontend

React + TypeScript frontend for the E-Bog'cha CRM system.

## Quick Start

```bash
npm install
npm run dev
```

Open http://localhost:5173 and enter a development actor UUID when prompted
(in development mode only).

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

`VITE_ACTOR_USER_ID` is only effective in development mode. In production,
stored actor values and the environment variable are ignored.

## Routes

| Path | Description |
|---|---|
| `/login` | Login page (non-functional by design; real auth deferred) |
| `/crm/leads` | CRM lead list (requires actor in development) |
| `/` | Redirects to `/crm/leads` |

## Architecture

Feature-oriented structure with strict dependency rules:
- `shared/` — reusable code (no imports from `features/`)
- `features/` — domain-specific modules
- `app/` — routing, providers, layout

## Key Behaviors

- **Search input**: Draft/committed model with 350ms debounce. Single-character
  input shows validation message without triggering API requests.
- **Date filters**: URL stores `YYYY-MM-DD` strings. ISO-8601 instants are
  computed only when making API requests.
- **Filter validation**: Centralized validation prevents API requests for
  invalid filter combinations (bad UUIDs, conflicting owner filters, etc.).
- **Actor isolation**: Development actor mechanism (localStorage, env var) is
  completely inactive in production builds.
- **Login**: Presents username/password fields. No authentication occurs.
  Backend auth is deferred.

## Branding

- Official logo: Oxu Kids
- Design tokens in `src/shared/styles/tokens.css`
- Optimized runtime assets in `public/branding/`

See `docs/07_UI_UX/FE001_FRONTEND.md` for full documentation.
