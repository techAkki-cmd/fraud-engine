#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

if [[ ! -f .env ]]; then
  echo "Missing .env. Copy .env.example to .env and fill required values first." >&2
  exit 1
fi

# Docker Compose gives already-exported shell variables precedence over values
# from --env-file. That can silently recreate Postgres with one password while
# services receive another, so clear DB-related overrides and force this reset
# to use the checked local .env file as the single source of truth.
unset POSTGRES_USER
unset POSTGRES_DB
unset POSTGRES_PASSWORD
unset FRAUD_ENGINE_POSTGRES_USER
unset FRAUD_ENGINE_POSTGRES_DB
unset FRAUD_ENGINE_POSTGRES_PASSWORD
unset SPRING_DATASOURCE_URL
unset SPRING_DATASOURCE_USERNAME
unset SPRING_DATASOURCE_PASSWORD

COMPOSE=(docker compose --env-file .env -f docker-compose.yml -f docker-compose.prod.yml)

echo "Stopping DB-backed services..."
"${COMPOSE[@]}" stop ai-fraud-agent ledger-service edge-gateway postgres || true

echo "Removing stale containers so old environment variables cannot be reused..."
docker rm -f \
  fraud-engine-ai-fraud-agent \
  fraud-engine-ledger-service \
  fraud-engine-edge-gateway \
  fraud-engine-postgres \
  2>/dev/null || true

echo "Removing local Postgres volume. This deletes local test database state."
docker volume rm fraud-engine-postgres-data 2>/dev/null || true

echo "Rebuilding and starting backend from current .env..."
"${COMPOSE[@]}" up -d --force-recreate postgres

echo "Verifying Postgres was initialized from the current .env..."
for _ in {1..60}; do
  health="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' fraud-engine-postgres 2>/dev/null || true)"
  if [[ "$health" == "healthy" ]]; then
    break
  fi
  sleep 2
done

docker exec fraud-engine-postgres sh -lc \
  'PGPASSWORD="$POSTGRES_PASSWORD" psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -tAc "select current_user, current_database();"'

"${COMPOSE[@]}" up -d --build --force-recreate \
  config-server \
  ai-fraud-agent \
  ledger-service \
  edge-gateway

echo "Waiting for services to become healthy..."
for container in \
  fraud-engine-postgres \
  fraud-engine-config-server \
  fraud-engine-ai-fraud-agent \
  fraud-engine-ledger-service \
  fraud-engine-edge-gateway; do
  for _ in {1..60}; do
    health="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$container" 2>/dev/null || true)"
    if [[ "$health" == "healthy" ]]; then
      echo "$container: healthy"
      break
    fi
    sleep 2
  done
done

echo "Final status:"
"${COMPOSE[@]}" ps postgres config-server ai-fraud-agent ledger-service edge-gateway
