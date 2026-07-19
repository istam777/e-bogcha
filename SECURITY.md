# Security Policy

E-Bog'cha processes personal data belonging to children, parents, guardians, and employees. Security issues must therefore be handled privately and promptly.

## Reporting a vulnerability

Do not disclose suspected vulnerabilities in public issues or pull requests.

Report them directly to the repository owner with:

- affected component;
- reproduction steps;
- expected and actual behavior;
- potential impact;
- suggested remediation, if known.

## Minimum security requirements

- Never commit passwords, API keys, JWT secrets, certificates, database dumps, or production `.env` files.
- Use least-privilege access for users, services, and database roles.
- Validate and authorize every request server-side.
- Audit security-sensitive and business-critical changes.
- Encrypt transport with TLS outside local development.
- Store passwords only as strong adaptive hashes.
- Keep dependencies and container images updated.
- Do not use real child or parent data in development or tests.

## Supported versions

Before the first production release, only the latest commit on the active development branch is supported. After release, supported versions will be documented here.
