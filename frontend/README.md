# Frontend

Next.js 16 (App Router) client for the Enterprise AI Knowledge Base.

It is deliberately a **client-rendered single-page app**. Next.js server features
are not used: every page is a client component that talks to the Spring Boot API
over HTTPS with a bearer token. Next.js is here because it was already the
project's frontend, not because the application needs a Node server.

## Running

```bash
cp .env.local.example .env.local
npm install
npm run dev
```

Then open <http://localhost:3000>. The backend must be running on the URL set in
`NEXT_PUBLIC_API_BASE_URL` (default `http://localhost:8080`).

## Scripts

| Command             | What it does                          |
| ------------------- | ------------------------------------- |
| `npm run dev`       | Development server with hot reload     |
| `npm run build`     | Production build                       |
| `npm run typecheck` | TypeScript check with no emit          |
| `npm run lint`      | ESLint                                 |

## Layout

```
app/
├── page.tsx            Chat: ask a question, read the answer and its sources
├── login/              Sign in
├── register/           Create an organization and its first administrator
├── documents/          Upload, list, and delete documents
├── dashboard/          Counts for the signed-in user's organization
└── admin/              Users and the audit log (administrators only)

components/
├── AuthProvider.tsx    Holds the session; verifies it against /api/auth/me
├── RequireAuth.tsx     Redirects signed-out users; hides screens by role
├── AppShell.tsx        Navigation shared by every signed-in page
└── SourceList.tsx      Renders answer citations

lib/
├── api.ts              Axios client, token handling, error messages
└── types.ts            TypeScript mirrors of the API's DTOs
```

## A note on the role checks here

`RequireAuth` hides screens a role cannot use. That is a usability measure, not a
security boundary — the real enforcement is on the server, where every endpoint
checks the role and the tenant carried by the signed token. Editing your way past
the client-side check gets you an empty page and a 403.

Full documentation, including setup of the backend, is in the
[root README](../README.md).
