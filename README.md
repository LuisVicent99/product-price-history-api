# product-price-history-api

REST API for products with historical prices. A price is valid for a date
interval; overlapping intervals for the same product and currency are
impossible by construction, enforced exclusively by a PostgreSQL
`EXCLUDE USING gist` constraint — never in Java code. The hot read path
(price in force at a date) resolves entirely in memory.

## Running it

Dev mode (Dev Services starts a disposable PostgreSQL and applies the schema):

```bash
mvn quarkus:dev
```

Tests (JVM, includes unit, endpoint, concurrency and architecture suites):

```bash
mvn test
```

Coverage report (JaCoCo, HTML and XML under `target/site/jacoco/`; the
last real run measured 61% instruction coverage — the uncovered remainder
is mostly the native-only reflection surface exercised by the ITs):

```bash
mvn test jacoco:report
```

Native build plus the same endpoint and concurrency suites re-run against
the native binary (on Windows/macOS the binary is Linux, so the integration
tests run it as a container; both extra flags are then required):

```bash
mvn verify -Dnative -Dquarkus.native.container-build=true -Dquarkus.container-image.build=true
```

Load test, end to end, from a clean machine with only Docker and make:

```bash
make build && make perf
```

`make build` builds the native image fully inside Docker
(`src/main/docker/Dockerfile.native`, multi-stage). `make perf` starts
PostgreSQL 18 (seeded with 1000 products), starts the app, prints the
startup time to first readiness 200, and runs the k6 scenario. `make clean`
resets everything. The ready-to-run request collection for the IDE is in
`api.http`.

## Stack and versions

- **Java 25 (LTS).** Latest long-term-support JDK, validated end to end
  with the native toolchain.
- **Quarkus 3.33 LTS** (currently 3.33.2.1), supported until March 2027.
  Chosen over the 3.37 regular release for stability and support window.
- **Mandrel 25** as the native-image builder
  (`ubi9-quarkus-mandrel-builder-image:jdk-25`), serial GC (`--gc=serial`)
  for low RSS. The runtime base image must be UBI9-based
  (`ubi9-quarkus-micro-image`): a UBI8 runtime lacks the glibc the UBI9
  builder links against.
- **No ORM.** Hand-written SQL on the reactive Vert.x PostgreSQL client:
  no entity state tracking, no dirty checking, no query generation — one
  prepared statement per operation and zero unnecessary work per request.
- **No Lombok.** Java records with compact constructors carry the model;
  mapping between layers is explicit static factories
  (`PriceView.from(price)`), no MapStruct and no reflective mapping.
- **Caffeine** (direct API, no quarkus-cache and no annotations) for the
  read cache, plus the `quarkus-caffeine` extension, which only registers
  Caffeine's generated cache classes for native reflection.

## Contract assumptions

- **`endDate` is INCLUSIVE.** "Valid until the 30th" means the whole 30th.
  That matches how humans and pricing teams speak. Internally PostgreSQL
  stores a half-open `daterange [init_date, end_date + 1)` in the generated
  `validity` column, so the inclusive contract and the range algebra never
  disagree. The explicit boundary case: a price ending `2026-06-30` and a
  new price starting `2026-06-30` → **409**; starting `2026-07-01` → **201**.
- **`endDate` null = open-ended validity** (price in force until further
  notice).
- **Two distinguishable 404s.** `code: PRODUCT_NOT_FOUND` (the product id
  does not exist) vs `code: PRICE_NOT_FOUND` (the product exists but no
  price covers the requested date), both RFC 7807 bodies.
- **`currency`** is stored (CHAR(3), default `EUR`) and participates in the
  exclusion constraint, but is outside the base API contract: requests may
  send it, responses expose a single `value`.
- **A one-day price is impossible** because the stated rule requires
  `initDate < endDate` strictly; with an inclusive `endDate`, a single-day
  validity would need `initDate == endDate`, which is rejected with 400.

## Design

- Ports live in `domain`: `ProductRepository`, `PriceRepository` and
  `PriceTimelineProvider` are plain interfaces exposing
  `java.util.concurrent.CompletionStage`. Implementations live outside:
  `Pg*` repositories in `infrastructure/persistence` (SQL only, row
  mappers in their own classes), the Caffeine cache in
  `infrastructure/cache`. `PricingService` is pure Java, wired by a CDI
  producer in `config`, so the domain carries no framework annotations.
- An ArchUnit test enforces it: nothing in `domain` may depend on `api`,
  `infrastructure`, `io.vertx`, `jakarta.ws.rs` or `com.github.benmanes`.
- It is deliberately not full hexagonal architecture: there is one driving
  side (HTTP) and one driven side (PostgreSQL), so use-case interfaces,
  command objects and per-adapter modules would add indirection with no
  second adapter to justify them. The load-bearing rule — dependencies
  point inward and the domain stays pure — is kept and verified.

## The overlap rule

`EXCLUDE USING gist (product_id WITH =, currency WITH =, validity WITH &&)`
over the generated `validity` daterange, with `btree_gist` for the equality
columns. The database enforces it and not Java because Java cannot: any
check-then-insert written in application code is a race between the check
and the insert, and locking a whole product's prices to close that race
serializes writers and still trusts every code path to remember the check.
The constraint is atomic with the insert itself, holds under any
concurrency, and holds for any other client that ever touches the table.
The insert is attempted directly (no pre-check) and SQLSTATE `23P01` is
translated to 409, `23503` to 404.

### Concurrency and the 40P01 deadlock

The concurrency test (two simultaneous identical overlapping inserts) at
some point returned `[201, 500]` instead of `[201, 409]`: both inserts had
entered the GiST index check at the same time, each waiting on the other's
uncommitted tuple, and PostgreSQL resolved it by aborting one with
SQLSTATE `40P01` (deadlock detected). The aborted insert is retried once;
by then the winner has committed and the retry receives the authoritative
`23P01` → 409. The constraint remains the only judge of overlap — the
retry just asks it again instead of guessing in Java.

## Performance

- **Read path without the database.** The current-price lookup goes through
  a Caffeine `AsyncLoadingCache` keyed by product id holding the domain
  `PriceTimeline`; a hit resolves with a binary search over epoch days —
  no SQL, no allocation beyond the response. A miss loads the full
  timeline with the single history query (one round-trip) and caches it.
- **Write-driven invalidation.** A successful price insert invalidates that
  product's entry strictly after the write is confirmed; a 409 or any
  failure invalidates nothing. `maximumSize(10_000)` and
  `expireAfterWrite(10m)` are a safety net against any missed
  invalidation, not the consistency mechanism. Failed loads are not
  cached (a missing product leaves no entry); Caffeine's WARN per failed
  load is muted to ERROR in logging config because a missing product is an
  expected 404.
- **Known limit: multiple replicas.** The cache is in-process. With N
  replicas, a write through one replica leaves up to 10 minutes of
  staleness in the others (the TTL bounds it). The single-writer,
  single-instance deployment this targets does not pay that cost; scaling
  out would need an external invalidation channel (e.g. LISTEN/NOTIFY)
  before raising the TTL.
- **The history endpoint reads the database directly**: it also returns
  product name and description, and duplicating their source of truth in
  the cache buys nothing on that cold path.

Query plans, measured on the seeded compose database (1000 products,
~10000 prices):

```
EXPLAIN ANALYZE SELECT id, product_id, amount, currency, init_date, end_date
FROM price WHERE product_id = 700 ORDER BY init_date;

 Index Scan using idx_price_product_init on price  (cost=0.29..27.75 rows=12 width=33) (actual time=0.101..0.106 rows=12.00 loops=1)
   Index Cond: (product_id = 700)
   Index Searches: 1
   Buffers: shared hit=6
 Planning Time: 3.098 ms
 Execution Time: 0.300 ms
```

```
BEGIN;
EXPLAIN ANALYZE INSERT INTO price (product_id, amount, currency, init_date, end_date)
VALUES (700, 9.99, 'EUR', DATE '2031-01-01', DATE '2031-01-31');
ROLLBACK;

 Insert on price  (cost=0.00..0.01 rows=0 width=0) (actual time=3.218..3.219 rows=0.00 loops=1)
   Buffers: shared hit=243 dirtied=2
   ->  Result  (cost=0.00..0.01 rows=1 width=96) (actual time=0.124..0.125 rows=1.00 loops=1)
 Trigger for constraint price_product_id_fkey: time=1.161 calls=1
 Execution Time: 4.419 ms
```

The timeline load walks `idx_price_product_init` (product_id, init_date),
which returns the rows already ordered — no sort node. The insert's plan is
the insert itself; the overlap check runs inside the `price_no_overlap`
GiST index, which is exactly where the contention (and the possible
`40P01`) lives.

Alternatives considered and dropped:

- **Redis**: a network hop on the hot path is the cost the cache exists to
  remove, and it adds an infrastructure dependency.
- **Infinispan**: distributed cache machinery for a single-instance,
  read-mostly workload is weight without benefit.
- **Precomputed JSON responses**: couples the cache to the HTTP
  representation and breaks the domain/API separation for microseconds.
- **ORM with second-level cache**: brings back entity management overhead
  the project explicitly avoids, with less control over invalidation.

## Load test

### Running it with Docker (how this exercise ships)

The whole flow needs nothing but Docker and make:

```bash
make build   # native image, built entirely inside Docker (first run takes a while)
make perf    # db + app up, startup time printed, k6 scenario executed
```

Without make, the recipes are:

```bash
docker build -f src/main/docker/Dockerfile.native -t product-price-history-api:native .
bash scripts/startup-time.sh
docker compose run --rm k6
```

On Windows, `bash` must be Git Bash, not the WSL relay that PowerShell
resolves by default.

```powershell
& "C:\Program Files\Git\bin\bash.exe" scripts/startup-time.sh
```

To repeat the load against the already-running stack, just re-run
`docker compose run --rm k6`. Runs accumulate inserted prices, so for a
clean measurement reset first with `docker compose --profile perf down -v`.
Watch consumption during the run with `make stats` (or
`docker stats pricing-app`). Do not lower the k6 service CPU limit: below
~2 CPUs the generator's own cgroup throttling inflates every latency it
measures (see the CPU comparison below).

### Running it with native k6 (the professional setup)

Containerizing the load generator is convenient for a self-contained
exercise, but the tool that measures should not share the resource budget
of the system it measures. With k6 installed on the host
(`winget install k6` / `brew install k6` / [k6.io docs](https://grafana.com/docs/k6/latest/set-up/install-k6/)),
point the same script at any environment:

```bash
k6 run -e BASE_URL=http://localhost:8080 perf/k6-script.js
```

The same script targets any deployment by changing `BASE_URL`:

```bash
k6 run -e BASE_URL=https://staging.example.com perf/k6-script.js
```

Offered load and duration live in the script
(`options.scenarios.main`: `rate`, `duration`), because the scenario is
defined there; k6's `--vus`/`--duration` shortcuts do not apply on top of
explicit scenarios. The thresholds and summary are identical to the Docker
flow; only the measurement runs free of container limits, which is exactly
what you want from the measuring side.

### What it measures

The scenario runs against the composed stack
(db capped at 1 GB/0.5 CPU, app at 256 MB/1 CPU, k6 at 1 GB/2 CPU):

- `setup()` creates 50 products with 10 contiguous prices each through the
  API and warms the cache with one lookup per product.
- Main scenario, constant arrival rate of 300 req/s for 60 s with 60
  pre-allocated VUs: 90% GET of the price in force for a random product
  (seed and setup mixed) at a random date between 2019 and 2028 (inside
  and outside any validity), 10% POST of new prices in guaranteed-free
  future ranges, of which a small slice deliberately overlaps to exercise
  the 409 path.
- Thresholds: `http_req_duration p(95) < 20 ms` and unexpected-response
  rate `< 1%`. Expected 404s and 409s are asserted with checks and marked
  expected via `expectedStatuses`, so they do not count as failures.

Interpreting the output: `throughput` is total requests over the 60 s
window, `latency p95` must stay under 20 ms, `expected responses` is the
share of requests answering exactly what their scenario predicts; any
threshold breach makes k6 (and `make perf`) exit non-zero.

Results of a real run on the reference machine (Windows 11, Docker
Desktop):

```
app-reported startup (binary alone):         0.078 s
docker start to first ready 200 from host:   514-1427 ms across runs

==================== load test summary ====================
total requests      19601
throughput          300 req/s
latency avg         5.24 ms
latency p95         16.41 ms
latency max         656.59 ms
expected responses  100.00 %
thresholds:
  http_req_duration: passed
  http_req_failed: passed
  unexpected_responses: passed
===========================================================

peak app memory under load (docker stats):
pricing-app 55.75MiB / 256MiB
```

The script tags every request with a stable `name` so k6 aggregates them
under a handful of metric series. Without that, the random product ids and
dates in the URLs produce one metric time series per request (k6 warns at
100k), and k6's own ingester — not the app — becomes the bottleneck,
inflating measured p95 by 2-3x. The tags are what make the measurement
report the system instead of the instrument.

The tens-of-milliseconds startup target is met by the binary itself: the
native executable reports `started in 0.080s` (0.047 s in the integration
test runs). The host-observed end-to-end figure is dominated by Docker
Desktop overhead on Windows — network creation and vpnkit port forwarding
— which is why `scripts/startup-time.sh` prints both numbers separately;
on a Linux host the gap between them shrinks to almost nothing.

### Why the k6 container gets 2 CPUs

The exercise statement caps auxiliary containers at 0.5 CPU. The declared
interpretation here: the load generator is the measuring instrument, not
an auxiliary service of the system under test — the system under test
(app at 256 MB/1 CPU, db at 1 GB/0.5 CPU) respects every limit, and only
the instrument gets room to measure without distorting what it measures.
A generator starved of CPU adds its own cgroup CFS throttling stalls
(100 ms periods) to every latency sample it records.

Measured evidence, same machine, same fixed 300 req/s scenario, with the
metric-cardinality fix already in place so this isolates CPU alone:

```
k6 at 0.5 CPU:   avg 7.47 ms   p95 35.56 ms   (threshold FAILED)
k6 at 2.0 CPU:   avg 4.10 ms   p95 11.39 ms   (threshold passed)
```

The 0.5-CPU generator adds its own cgroup CFS throttling stalls (100 ms
periods) to every sample and pushes p95 past the 20 ms threshold with app
and db half idle in both runs — the difference is the instrument, not the
system. The scenario also offers a fixed 300 req/s (constant arrival rate)
rather than a closed loop, so latency is measured at a defined load
instead of at self-inflicted saturation.

## Project structure

```
db/init/                  canonical DDL (01-schema.sql) and compose-only seed (02-seed.sql)
perf/k6-script.js         load test scenario
scripts/startup-time.sh   measures docker start -> first readiness 200
src/main/docker/          Dockerfile.native (multi-stage build), Dockerfile.native-runtime (prebuilt binary)
src/main/java/com/product/pricing/
  api/                    JAX-RS resources, DTOs (records + from(...)), RFC 7807 mappers
  config/                 CDI producer wiring the domain service
  domain/                 pure Java: model, errors, ports, PricingService
  infrastructure/
    cache/                Caffeine adapter of PriceTimelineProvider
    persistence/          Pg* repositories and row mappers (hand-written SQL)
src/main/resources/db/init/01-schema.sql   classpath copy for Dev Services (keep in sync)
src/test/java/            unit, endpoint, concurrency, cache, architecture tests and native ITs
api.http                  IDE-ready request collection
docker-compose.yml        db + app + k6 with explicit resource limits
Makefile                  build / up / perf / test / verify-native / clean
```
