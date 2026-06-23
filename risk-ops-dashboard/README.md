# Risk Ops Dashboard

This is the analyst-facing dashboard for the Agentic Payment Integrity & Fraud Engine. It provides the live command center for submitting demo payment payloads, viewing Gateway SSE fraud decisions, and inspecting AI reasoning for `REVIEW` and `BLOCK` transactions.

For the full system overview, architecture, and runbook, use the root project README.

## Local Development

```bash
npm run dev
```

The Docker Compose stack serves the dashboard on:

```text
http://localhost:3001
```

Useful checks:

```bash
npm run lint
npm run build
```

## Runtime Configuration

The dashboard expects Keycloak/NextAuth settings and Gateway URLs from environment variables or Compose defaults. The main variables are:

- `NEXTAUTH_URL`
- `NEXTAUTH_SECRET`
- `DASHBOARD_OIDC_CLIENT_ID`
- `DASHBOARD_OIDC_CLIENT_SECRET`
- `NEXT_PUBLIC_GATEWAY_URL`
- `NEXT_PUBLIC_GATEWAY_INGEST_URL`

The Demo Simulator now ships with India-market payment templates using INR, UPI/card flows, and local merchant/account scenarios.
