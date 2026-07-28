# product-price-history-api

REST API for products with historical prices. A price is valid for a date
interval; overlapping intervals for the same product and currency are
impossible by construction, enforced exclusively by a PostgreSQL
`EXCLUDE USING gist` constraint — never in Java code.

## Run in dev mode

```bash
./mvnw quarkus:dev
```

Dev Services starts a disposable PostgreSQL container and applies
`db/init/01-schema.sql` automatically.

## Native image

```bash
./mvnw package -Dnative -Dquarkus.native.container-build=true
docker build -f src/main/docker/Dockerfile.native -t product-price-history-api .
docker run -i --rm -p 8080:8080 product-price-history-api
```

## Technical decisions

- **Java 25 (LTS).** Latest long-term-support JDK; validated end to end with
  the native toolchain.
- **Quarkus 3.33 LTS** (currently 3.33.2.1), supported until March 2027.
  Chosen over the 3.37 regular release for stability and support window.
- **Mandrel 25** as the native-image builder
  (`ubi9-quarkus-mandrel-builder-image:jdk-25`), serial GC (`--gc=serial`)
  for low RSS.
- **No ORM.** Data access is hand-written SQL on the reactive Vert.x
  PostgreSQL client. The domain package is pure Java: repository interfaces
  live in `domain` and are implemented by `Pg*` classes in
  `infrastructure/persistence`, so the domain never imports Vert.x, JAX-RS
  or DTO types. Asynchrony crosses the domain boundary as
  `java.util.concurrent.CompletionStage`.
- **Date semantics.** The API exposes `endDate` as INCLUSIVE; `endDate`
  null means open-ended validity. PostgreSQL stores a half-open
  `daterange [init_date, end_date + 1)` in the generated `validity` column,
  which feeds the GiST exclusion constraint. `init_date < end_date`
  is required, so a same-day interval is invalid.
- **Read cache.** The current-price lookup resolves without touching
  PostgreSQL in the hot case: a Caffeine `AsyncLoadingCache` (raw API, no
  quarkus-cache) in `infrastructure/cache` keyed by product id holds the
  domain `PriceTimeline`, loaded on miss with the single history query and
  resolved in memory by binary search. The database remains the source of
  truth: a successful price insert invalidates the product entry after the
  write is confirmed (a 409 or any failure invalidates nothing), and
  `maximumSize(10_000)` plus `expireAfterWrite(10m)` act as a safety net
  against any missed invalidation. Failed loads are not cached; Caffeine
  logs each one as WARN, which is muted to ERROR in configuration because a
  lookup of a missing product is an expected 404, not an incident. The domain
  sees the cache only through the `PriceTimelineProvider` port. The history
  endpoint keeps reading the database directly because it also returns the
  product name and description, whose source of truth is not duplicated in
  the cache.
- **Overlap handling.** Inserts do not pre-check for overlap: the insert is
  attempted and SQLSTATE `23P01` (exclusion violation) is translated to a
  409, `23503` (foreign key) to a 404. This keeps the hot path at one
  round-trip and is race-free under concurrency. Two simultaneous
  overlapping inserts can deadlock inside the GiST index, in which case
  PostgreSQL aborts one of them with `40P01`; that insert is retried once,
  and the retry receives the authoritative verdict (`23P01` if the winner
  overlaps it), so the constraint stays the only judge of overlap and the
  client still sees exactly one 201 and one 409.
- **Schema copies.** The canonical DDL is `db/init/01-schema.sql`. Dev
  Services resolves `quarkus.datasource.devservices.init-script-path`
  against the classpath, so an identical copy lives at
  `src/main/resources/db/init/01-schema.sql`; keep both in sync.
- **Errors** follow RFC 7807 (`application/problem+json`) with a `code`
  extension distinguishing `PRODUCT_NOT_FOUND` from `PRICE_NOT_FOUND`.
