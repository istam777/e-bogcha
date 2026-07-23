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
      ActorProvider.tsx    — Actor context (DEV-only localStorage + env)
    layouts/
      AppLayout.tsx    — Sidebar + Header + content shell
      AuthenticatedLayout.tsx — Redirects to /login when actor not configured
      Sidebar.tsx      — Navigation sidebar
      Header.tsx       — Top header with actor display
    pages/
      NotFoundPage.tsx — 404 page
  features/
    auth/pages/
      LoginPage.tsx    — Login form (non-functional, presentational only)
    crm/leads/
      api/
        leads-api.ts       — API fetch function
        query-params.ts    — Serialization, defaults, filter helpers
      model/
        labels.ts          — Status/source/owner-state Uzbek labels
      lib/
        dates.ts           — Date formatting, conversion, and validation
        url-state.ts       — URL ↔ LeadSearchParams sync
        filter-validation.ts — Centralized filter validation
      ui/
        SearchInput.tsx    — Draft/committed search with debounce
        FilterBar.tsx      — Filter controls
        LeadTable.tsx      — Desktop table
        LeadCards.tsx      — Mobile cards
        Pagination.tsx     — Pagination controls
        EmptyState.tsx     — Empty state display
      pages/
        LeadListPage.tsx   — Main lead list page
  shared/
    api/client.ts          — Typed fetch client with error parsing
    config/env.ts          — Environment variable access (isDevelopment flag)
    lib/actor.ts           — Actor UUID management (DEV-only resolution)
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
| `VITE_ACTOR_USER_ID` | (empty) | Default actor UUID (DEV only) |

See `.env.example` for reference.

## Vite Proxy

In development, `/api` requests are proxied to `VITE_DEV_PROXY_TARGET`.
In production, the app expects the backend to be served at the same origin.

## Login and Authentication

The login page (`/login`) provides a username and password form that is
**currently non-functional by design**. Real authentication is deferred.

- No Google, social login, or self-registration
- Empty fields display required-field validation messages
- Valid-looking credentials show a notice that backend auth is pending
- No token is created; no password is persisted
- The login page is outside `AuthenticatedLayout`

## Temporary Actor Mechanism (Development Only)

The backend requires `X-Actor-User-Id` header. This is NOT authentication.

**Development mode:**
- localStorage actor is supported
- `VITE_ACTOR_USER_ID` env var is supported
- Developer actor form is shown on login page
- Actor header is attached to CRM API requests

**Production mode:**
- Stored actor values are ignored
- `VITE_ACTOR_USER_ID` is ignored
- `setActor` cannot configure development access
- `/crm/leads` redirects to `/login`
- Developer actor UI is absent

Resolution order (DEV only):
1. `localStorage` key `ebogcha_actor_user_id`
2. `VITE_ACTOR_USER_ID` env var
3. No actor (redirect to login)

The actor logic is isolated in `shared/lib/actor.ts` and can be replaced
when real authentication is implemented.

## CRM Lead Route

`/crm/leads` renders the lead list page, wrapped by `AuthenticatedLayout`.

Features:
- Server-side search with debounce (350ms, draft/committed model)
- Filters: status, source, owner state, overdue, date range, branch ID, operator ID
- URL-synchronized filter state (back/forward navigation works)
- Desktop table + mobile cards
- Pagination (10/20/50/100 per page)
- Loading skeleton, empty states, error display
- Active filter count indicator
- Centralized filter validation blocks invalid API requests

## Search Input

Uses a draft/committed-value model:
- Every keystroke is visible locally
- Trimmed empty input clears the search query
- Trimmed one-character input shows validation message, does not call API
- Two-or-more character input commits after 350ms debounce
- Clear button immediately clears draft and committed query
- External URL changes synchronize the displayed draft

## Date Range Conversion

- `createdFrom` and `createdTo` are stored in the URL as `YYYY-MM-DD` strings
- Only converted to ISO-8601 instants when constructing API requests
- `createdFrom`: local date → start of day → ISO-8601 instant (inclusive)
- `createdTo`: local date → start of next day → ISO-8601 instant (exclusive)
- Invalid date strings in the URL are silently ignored (no crash, no API request)

This matches the backend's inclusive lower / exclusive upper boundary semantics.

## Filter Validation

A centralized pure validation function (`filter-validation.ts`) checks:
- `branchId` UUID format
- `ownerOperatorId` UUID format
- `ownerOperatorId` + `UNASSIGNED` conflict (auto-clears operator on state change)
- `createdFrom` and `createdTo` date format
- `createdFrom` must not be after `createdTo`
- `page` and `size` within supported bounds
- Search query minimum length

Invalid filters prevent API requests and display inline error messages.

## API Integration

- Uses `GET /api/v1/crm/leads` with typed query parameters
- Attaches `X-Actor-User-Id` to all CRM requests (DEV only)
- Parses `application/problem+json` error responses
- Query is enabled only when actor is configured AND all filters are valid

## Testing

```bash
npm run test -- --run          # Run all tests
npm run test -- --run src/path # Run specific test file
```

Tests cover:
- API query parameter serialization
- Default parameter omission
- Filter state management
- Search input with realistic user interactions (userEvent.type)
- Debounce behavior and API request counting
- UUID validation
- Date conversion and format validation
- Status/source label mapping
- Pagination metadata
- API error parsing
- Actor localStorage behavior
- Component rendering with MSW
- Login form validation
- Development actor access
- Filter validation blocking invalid requests

## Official Branding

- Logo source: `docs/07_UI_UX/branding/logo.png`
- Runtime logo: `frontend/public/branding/oxu-kids-logo.png` (optimized)
- Runtime favicon: `frontend/public/branding/oxu-kids-favicon.png` (optimized)
- Design tokens: navy-950 #061653, navy-900 #071D64, primary #152F8F, gold #C9A664, background #FAF7F2

## Deferred Features

The following are NOT implemented in FE-001:
- Real backend authentication
- User and role administration
- SUPER_ADMIN and ADMIN account-creation policy
- Password security (hashing, rotation, complexity)
- Lead creation, editing, deletion
- Status transitions
- Lead acceptance
- Ownership transfer
- Duplicate resolution
- Dashboard / statistics
- Admissions, finance, attendance, staff, settings modules
