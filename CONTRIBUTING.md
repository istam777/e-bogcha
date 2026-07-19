# Contributing to E-Bog'cha

## Branches

- `main`: production-ready releases only.
- `develop`: integration branch.
- `feature/<scope>-<description>`: new work.
- `bugfix/<scope>-<description>`: non-production fixes.
- `hotfix/<description>`: urgent production fixes from `main`.

Direct feature work on `main` is not allowed.

## Development flow

1. Create a branch from `develop`.
2. Implement one scoped task.
3. Add or update tests and documentation.
4. Run all relevant checks locally.
5. Open a pull request into `develop`.
6. Address review comments.
7. Merge only after required checks pass.

## Commit convention

Use Conventional Commits:

- `feat:` new behavior
- `fix:` bug fix
- `docs:` documentation only
- `refactor:` behavior-preserving code change
- `test:` tests
- `chore:` tooling or maintenance
- `ci:` CI/CD changes

Examples:

```text
feat(crm): add lead assignment service
fix(auth): reject expired refresh tokens
docs(database): document lead lifecycle
```

## Pull request requirements

Each pull request must include:

- purpose and scope;
- related task or issue;
- implementation summary;
- database or API impact;
- test evidence;
- rollback notes when relevant.

Do not commit credentials, production data, generated build output, IDE folders, or local environment files.
