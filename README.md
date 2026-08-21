# E-Commerce Store

A full-stack e-commerce store built test-first (TDD). Server-rendered storefront on a relational backend, with enterprise identity via Keycloak.

**Stack:** Java 21 · Spring Boot 3.3 · PostgreSQL + Flyway + Spring Data JPA · Keycloak (OAuth2 / OIDC) · Thymeleaf + Bootstrap 5 · Stripe (mock mode) · JUnit 5 + MockMvc

## Features

- **Catalog** — Postgres **full-text search** (ranked) with **substring/partial matching** and **autocomplete**, category filter, and pagination.
- **Carts** — persistent per-user carts *and* cookie-based **guest carts** that **merge into the user cart on login**.
- **Coupons** — percentage / fixed discount codes, live-revalidated, snapshotted onto orders.
- **Checkout** — single-transaction, **concurrency-safe atomic stock decrement**, price/line snapshots, and **partial-line checkout** (order some quantities, leave the rest).
- **Stock reservation** — checkout holds stock; a scheduled job releases lapsed, unpaid reservations.
- **Payments** — Stripe integration with a mock mode for local dev (`PENDING → PAID`).
- **Errors** — RFC 7807 `ProblemDetail` for the API; friendly HTML error pages for the web.

## Architecture highlights

- **Dual Spring Security chains** — session + OAuth2 **login** for the Thymeleaf storefront, stateless **JWT resource server** for `/api/**`. Roles map from Keycloak `realm_access.roles` through one shared helper so both chains stay in sync.
- **Concurrency** — `UPDATE products SET stock = stock - :qty WHERE stock >= :qty` makes check-and-decrement a single row-locked statement (no oversell), plus a `@Version` optimistic-lock column.
- **Order integrity** — order items snapshot name/SKU/unit price; `subtotal` is a DB **generated column**; addresses stored as JSONB.
- **Schema owned by Flyway** (`ddl-auto: validate`), migrations `V1…V9`.

## Getting started

**Prerequisites:** JDK 21, PostgreSQL, Keycloak, Maven.

1. **Database** — create the DB:
   ```sql
   CREATE DATABASE ecommerce_db;
   ```
2. **Keycloak** — run on port **8180**, realm `ecommerce-realm`, confidential client `ecommerce-store`, roles `ADMIN`/`USER`. Enable **Realm settings → Login → User registration** for customer self-signup.
3. **Secrets** — put local secrets in `src/main/resources/application-local.yml` (git-ignored):
   ```yaml
   spring:
     datasource:
       password: <your-postgres-password>
     security:
       oauth2:
         client:
           registration:
             keycloak:
               client-secret: <keycloak-client-secret>
   ```
4. **Run** — start Postgres + Keycloak, then:
   ```bash
   mvn spring-boot:run
   ```
   The app serves at **http://localhost:8080** (Windows users: `start-store.bat` boots Postgres, Keycloak, and the app together).

## Tests

```bash
mvn clean test
```
The suite (38 tests) requires a running Postgres; it is **independent of Keycloak** (test config uses static OAuth2 endpoints), so the build stays green without a live identity server.

## Configuration secrets

Only `${ENV_VAR}` placeholders with harmless dev defaults are committed. Real secrets live in the git-ignored `application-local.yml`.
