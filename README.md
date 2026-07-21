# Atlas — Travel Cart Service

> REST-only pre-booking selection (one flight + one hotel) with a price snapshot and TTL.

Part of **[Atlas](https://github.com/atlas-event-lab)** (ADR-0003).

## Responsibilities

- Hold a user's pre-booking selection (1 flight + 1 hotel, with hotel stay dates — ADR-0011)
  and a price snapshot, expiring via TTL.
- Convert a cart into a booking (hands the selection to Booking).
- Deliberately **REST-only** — no Kafka.

## Tech

Java 21 · Spring Boot · Spring Data JPA · PostgreSQL (`travel_cart_db`) · Keycloak JWT.

## API

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/carts/{cartId}` | Fetch a cart |
| PUT | `/api/v1/carts/{cartId}/flight` · `/hotel` | Set flight / hotel selection |
| DELETE | `/api/v1/carts/{cartId}/items/{itemId}` · `/items` | Remove item(s) |
| POST | `/api/v1/carts/{cartId}/conversion` | Convert cart → booking |

Uses an exchange-rate client for the price snapshot.

## Events

None (REST-only). Conversion calls the Booking service directly.

## Data

Owns `travel_cart_db` (database-per-service).

## Run locally

```bash
docker compose up travel-cart-service
```

Env: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `KEYCLOAK_ISSUER_URI`.

## License

Apache-2.0 — see [`LICENSE`](./LICENSE).
