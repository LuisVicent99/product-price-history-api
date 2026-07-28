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
  which feeds both the GiST exclusion constraint and the
  `validity @> date` lookup of the price in force. `init_date < end_date`
  is required, so a same-day interval is invalid.
- **Overlap handling.** Inserts do not pre-check for overlap: the insert is
  attempted and SQLSTATE `23P01` (exclusion violation) is translated to a
  409, `23503` (foreign key) to a 404. This keeps the hot path at one
  round-trip and is race-free under concurrency.
- **Schema copies.** The canonical DDL is `db/init/01-schema.sql`. Dev
  Services resolves `quarkus.datasource.devservices.init-script-path`
  against the classpath, so an identical copy lives at
  `src/main/resources/db/init/01-schema.sql`; keep both in sync.
- **Errors** follow RFC 7807 (`application/problem+json`) with a `code`
  extension distinguishing `PRODUCT_NOT_FOUND` from `PRICE_NOT_FOUND`.
