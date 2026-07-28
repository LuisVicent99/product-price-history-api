#!/usr/bin/env bash
set -euo pipefail

docker compose up -d db
until [ "$(docker inspect -f '{{.State.Health.Status}}' pricing-db)" = "healthy" ]; do
  sleep 0.2
done

docker compose up -d app
until curl -fsS http://localhost:8080/q/health/ready >/dev/null 2>&1; do
  sleep 0.02
done
ready_ms=$(date +%s%3N)

started_at=$(docker inspect -f '{{.State.StartedAt}}' pricing-app)
started_ms=$(date -d "$started_at" +%s%3N)

app_reported=$(docker logs pricing-app 2>&1 | grep -oE 'started in [0-9.]+s' | head -1)

echo "app container started at:                    $started_at"
echo "first readiness 200 observed from host:      $(date -d "@$((ready_ms / 1000))" -u +%Y-%m-%dT%H:%M:%S).$((ready_ms % 1000))Z"
echo "app-reported startup (binary alone):         ${app_reported#started in }"
echo "docker start to first ready 200 from host:   $((ready_ms - started_ms)) ms"
