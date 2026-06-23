# Production-Style Local Runtime

Use this mode when you want the local stack to call the real Gemini provider for analyst reasoning. The important boundary stays the same: deterministic Java rules decide `SAFE`, `REVIEW`, or `BLOCK`; Gemini only writes the short explanation shown to analysts.

## Secret Handling

Do not commit Gemini credentials. If a key was ever pasted into chat, logs, or a tracked file, rotate it before using this run mode.

```bash
cp .env.example .env
```

Edit `.env` and set:

```bash
AI_FRAUD_AGENT_PROFILES=prod
FRAUD_AGENT_AI_PROVIDER_NAME=google-genai
FRAUD_AGENT_SEED_MOCK_DATA=false
GEMINI_API_KEY=<rotated-runtime-secret>
```

## Validate Configuration

```bash
git check-ignore .env
rg "AQ\\.|GEMINI_API_KEY=.*[^-]$" . --glob '!target' --glob '!node_modules' --glob '!.next'
docker compose --env-file .env -f docker-compose.yml -f docker-compose.prod.yml config --quiet
```

The `rg` command should not print a real key from tracked files. If it does, stop and rotate the key before continuing.

## Start The Stack

```bash
docker compose --env-file .env -f docker-compose.yml -f docker-compose.prod.yml up -d --build
```

If only `.env` changed, recreating the AI service is usually enough:

```bash
docker compose --env-file .env -f docker-compose.yml -f docker-compose.prod.yml up -d --build --force-recreate ai-fraud-agent
```

## Verify Runtime

```bash
curl http://localhost:8082/actuator/health
docker logs fraud-engine-ai-fraud-agent | grep -i "google-genai"
```

Open the dashboard:

```text
http://localhost:3001
```

Submit the India-market SAFE, REVIEW, and BLOCK templates from the Demo Simulator. SAFE should reach `payment-cleared`, REVIEW should reach `payment-review`, BLOCK should reach `payment-blocked`, and only SAFE should be posted by the ledger.
