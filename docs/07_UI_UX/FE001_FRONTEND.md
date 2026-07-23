# FE-001 — Frontend Foundation and CRM Lead List

## Scope

This stage establishes the React/TypeScript frontend architecture and implements
the read-only CRM lead list page. No backend modifications were made.

## Stack

- React 19 + TypeScript 6 (strict mode)
- Vite 8
- React Router 7
- TanStack Query 5
- Lucide React icons
- Vitest + React Testing Library + MSW 2
- ESLint + Prettier
- npm

## Architecture

```
frontend/src/
  app/
    App.tsx            — QueryClient + ActorProvider + Router
    router.tsx         — Route definitions
    providers/
      ActorProvider.tsx    — Actor context (localStorage + env)
      ActorSetupScreen.tsx — Development actor setup
    layouts/
      AppLayout.tsx    — Sidebar + Header + Outlet
      Sidebar.tsx      — Navigation sidebar
      Header.tsx       — Top header with actor display
    pages/
      NotFoundPage.tsx — 404 page
  features/
    crm/leads/
      api/
        leads-api.ts       — API fetch function
        query-params.ts    — Serialization, defaults, filter helpers
      model/
        labels.ts          — Status/source/owner-state Uzbek labels
      lib/
        dates.ts           — Date formatting and conversion
        url-state.ts       — URL ↔ LeadSearchParams sync
      ui/
        SearchInput.tsx    — Search input with validation
        FilterBar.tsx      — Filter controls
        LeadTable.tsx      — Desktop table
        LeadCards.tsx      — Mobile cards
        Pagination.tsx     — Pagination controls
        EmptyState.tsx     — Empty state display
      pages/
        LeadListPage.tsx   — Main lead list page
  shared/
    api/client.ts          — Typed fetch client with error parsing
    config/env.ts          — Environment variable access
    lib/actor.ts           — Actor UUID management
    types/api.ts           — API TypeScript types
    ui/                    — Reusable UI components
    styles/                — CSS tokens and global styles
  tests/
    server.ts              — MSW server setup
    handlers.ts            — MSW request handlers
    vitest-setup.ts        — Test setup with MSW
```

## Development Commands

```bash
cd frontend
npm install
npm run dev          # Start dev server with proxy
npm run typecheck    # TypeScript check
npm run lint         # ESLint
npm run test -- --run  # Run tests
npm run build        # Production build
```

## Environment Variables

| Variable | Default | Description |
|---|---|---|
| `VITE_API_BASE_URL` | (empty) | Backend API base URL |
| `VITE_DEV_PROXY_TARGET` | `http://localhost:8080` | Vite proxy target |
| `VITE_ACTOR_USER_ID` | (empty) | Default actor UUID |

See `.env.example` for reference.

## Vite Proxy

In development, `/api` requests are proxied to `VITE_DEV_PROXY_TARGET`.
In production, the app expects the backend to be served at the same origin.

## Temporary Actor Mechanism

The backend requires `X-Actor-User-Id` header. This is NOT authentication.

Resolution order:
1. `localStorage` key `ebogcha_actor_user_id`
2. `VITE_ACTOR_USER_ID` env var
3. Blocking setup screen

The actor logic is isolated in `shared/lib/actor.ts` and can be replaced
when real authentication is implemented.

## CRM Lead Route

`/crm/leads` renders the lead list page.

Features:
- Server-side search with debounce (350ms)
- Filters: status, source, owner state, overdue, date range, branch ID, operator ID
- URL-synchronized filter state (back/forward navigation works)
- Desktop table + mobile cards
- Pagination (10/20/50/100 per page)
- Loading skeleton, empty states, error display
- Active filter count indicator

## API Integration

- Uses `GET /api/v1/crm/leads` with typed query parameters
- Attaches `X-Actor-User-Id` to all CRM requests
- Parses `application/problem+json` error responses
- Retries failed requests once

## Date Range Conversion

- `createdFrom`: Local date → start of day → ISO-8601 instant (inclusive)
- `createdTo`: Local date → start of next day → ISO-8601 instant (exclusive)

This matches the backend's inclusive lower / exclusive upper boundary semantics.

## Testing Commands

```bash
npm run test -- --run          # Run all tests
npm run test -- --run src/path # Run specific test file
```

55 unit and integration tests covering:
- API query parameter serialization
- Default parameter omission
- Filter state management
- Search input validation
- UUID validation
- Date conversion
- Status/source label mapping
- Pagination metadata
- API error parsing
- Actor localStorage behavior
- Component rendering with MSW

## Deferred Features

The following are NOT implemented in FE-001:
- Lead creation, editing, deletion
- Status transitions
- Lead acceptance
- Ownership transfer
- Duplicate resolution
- Dashboard / statistics
- Real authentication
- Admissions, finance, attendance, staff, settings modules
