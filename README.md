# 🌦️ Weather API Aggregator

> A Spring Boot backend API that aggregates weather data from multiple external sources, with automatic fallback, a circuit breaker, JWT authentication and normalized error handling — designed to keep working even when an external source fails.

Weather API Aggregator queries a primary weather provider (Open-Meteo) and falls back automatically to a secondary one (OpenWeatherMap) if the first is down, each call protected by a Resilience4j circuit breaker and retry with exponential backoff. On top of that sits a full per-user layer — JWT authentication, search history, favorite cities and unit preferences backed by PostgreSQL — plus an in-memory cache, per-user rate limiting and a provider-comparison endpoint that shows the same city side by side across every configured source.

## 📦 What's Inside

- 🔎 Current weather lookup by city, with unit normalization (Celsius/km-h or Fahrenheit/mph)
- 🧩 Providers decoupled behind a Strategy/Adapter interface — swapping or adding a provider never touches the controller or the API contract
- 🔁 **Automatic fallback between providers** (Open-Meteo → OpenWeatherMap): if the primary fails, the request is served by the secondary one transparently
- ⚡ **Circuit breaker + retry with exponential backoff** (Resilience4j) per provider — a provider that's systematically failing stops being called for a few seconds instead of piling up load, and transient errors are retried before giving up on that provider
- ⚖️ **Provider comparison endpoint** — the same city, side by side, across every configured provider, with a per-provider error entry if one fails instead of failing the whole request
- 🌡️ Unit normalization across providers — OpenWeatherMap natively returns Kelvin and m/s; the conversion to Celsius/Fahrenheit and km/h/mph happens in the application, never on the provider's side
- ⚡ In-memory cache (Caffeine), configurable TTL, with an explicit `fromCache` flag showing whether a response came from cache
- 📊 Aggregate stats endpoint — total users, searches, favorites, the most-searched city and live cache hit/miss counts
- 🔐 JWT authentication (register/login), passwords hashed with BCrypt
- 🕘 Per-user search history and favorite cities (PostgreSQL, Flyway migrations)
- ⚙️ Per-user unit preference — omitting `units` on a search falls back to the saved preference
- 🚧 Per-user rate limiting (Bucket4j), configurable, with a normalized `429` response
- 🚦 Normalized errors that never leak the raw external provider error: `404` city not found, `502` provider unavailable, `429` quota/rate limit exceeded, `400` invalid input, `401` unauthenticated, `409` conflict (duplicate email/favorite)
- 🗺️ Weather descriptions translated from Open-Meteo's WMO weather codes (OpenWeatherMap already returns its own description)
- 📑 Interactive API documentation via Swagger/OpenAPI
- ✅ Unit, integration (WireMock + a real PostgreSQL instance) and end-to-end tests — including one that forces a real circuit breaker trip — at ~97% line coverage

## 🛠️ Tech Stack

![Java](https://img.shields.io/badge/Java%2021-ED8B00?style=flat&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot%203-6DB33F?style=flat&logo=springboot&logoColor=white)
![Spring Security](https://img.shields.io/badge/Spring%20Security-6DB33F?style=flat&logo=springsecurity&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-4169E1?style=flat&logo=postgresql&logoColor=white)
![Flyway](https://img.shields.io/badge/Flyway-CC0200?style=flat&logo=flyway&logoColor=white)
![JWT](https://img.shields.io/badge/JWT-000000?style=flat&logo=jsonwebtokens&logoColor=white)
![Resilience4j](https://img.shields.io/badge/Resilience4j-CC0000?style=flat)
![Maven](https://img.shields.io/badge/Maven-C71A36?style=flat&logo=apachemaven&logoColor=white)
![Caffeine](https://img.shields.io/badge/Caffeine%20Cache-6F4E37?style=flat)
![Bucket4j](https://img.shields.io/badge/Bucket4j-2E7D32?style=flat)
![JUnit5](https://img.shields.io/badge/JUnit%205-25A162?style=flat&logo=junit5&logoColor=white)
![WireMock](https://img.shields.io/badge/WireMock-3E863D?style=flat)
![OpenAPI](https://img.shields.io/badge/OpenAPI%20%2F%20Swagger-85EA2D?style=flat&logo=swagger&logoColor=black)

## 🏗️ Architecture

```
weather-api/
├── src/main/java/com/vidi/weather/
│   ├── controller/            # Weather (+ history/favorites/compare), User, Auth
│   ├── service/                 # cache/provider/fallback orchestration, resilience, users, history, favorites
│   ├── provider/                # Strategy/Adapter interface + Open-Meteo + OpenWeatherMap
│   ├── security/                # JWT, filters, rate limiting, UserDetails
│   ├── entity/                  # JPA entities (User, SearchHistoryEntry, Favorite)
│   ├── repository/              # Spring Data JPA
│   ├── model/                   # internal domain (immutable)
│   ├── dto/                     # API contract (responses, errors, auth, preferences, comparison)
│   ├── config/                  # cache, RestTemplate, security, rate limit, properties
│   ├── exception/                # domain exceptions + global handler
│   └── util/                    # weather code mapping + unit conversion
├── src/main/resources/db/migration/  # Flyway migrations
├── src/test/java/                # unit, repository (real Postgres), WireMock, MockMvc, security, fallback/circuit breaker tests
├── LICENSE
└── pom.xml
```

### Why these choices

- **Strategy/Adapter for providers**: `WeatherProvider` is the only contract the rest of the app knows about. Open-Meteo and OpenWeatherMap each normalize their own response shape into the same `WeatherData`, so adding a third provider later is additive, not a rewrite.
- **Caffeine over Redis**: for a single-instance API, an in-memory cache is enough and avoids standing up extra infrastructure. Redis is the natural next step once the app runs on more than one instance and needs a shared cache.
- **Open-Meteo as the primary provider**: free, no API key required, so the project runs out of the box with zero setup friction. OpenWeatherMap is the secondary/fallback provider, which does need a free API key (see *How to Run*).
- **Resilience4j circuit breaker + retry, not a hand-rolled fallback loop**: each provider gets its own breaker and retry policy configured declaratively in `application.yml`, so a systematically failing provider is skipped instead of retried forever, while transient errors (a single dropped request) still get absorbed before falling back.
- **PostgreSQL + Flyway over JPA auto-DDL**: `ddl-auto: validate` plus a versioned migration means the schema is explicit and reviewable, not implicitly inferred from entity annotations.
- **Stateless JWT over sessions**: no server-side session store to scale, and CSRF protection is correctly disabled for this reason — it protects cookie-based sessions, which this API doesn't use.
- **Immutable entities**: JPA entities have no public setters; updates (e.g. changing a user's preferred units) go through a `withX` copy method and `repository.save(...)`, keeping the "never mutate in place" rule even inside Hibernate-managed objects.

## 🌐 API

```
POST /api/v1/auth/register                 — register (returns a JWT)
POST /api/v1/auth/login                    — log in (returns a JWT)

GET  /api/v1/weather?city=&units=          — current weather, with automatic fallback (authenticated)
GET  /api/v1/weather/compare?city=&units=  — same city, side by side, across every provider
GET  /api/v1/weather/history               — search history
GET  /api/v1/weather/favorites             — list favorites
POST /api/v1/weather/favorites             — add a favorite

GET  /api/v1/user/preferences              — get preferences
POST /api/v1/user/preferences              — update preferences

GET  /api/v1/stats                         — aggregate usage stats (users, searches, favorites, cache hit rate)
```

| Scenario | Status |
|---|---|
| Success | `200` / `201` |
| Unauthenticated | `401` |
| City not found | `404` |
| Conflict (duplicate email/favorite) | `409` |
| External provider unavailable | `502` |
| Provider quota or rate limit exceeded | `429` |
| Invalid or missing parameter | `400` |

## 🚀 How to Run

Prerequisites: Java 21, Maven and a local PostgreSQL instance.

```bash
# 1. Clone the repository
git clone https://github.com/VidiPT89/WeatherAPI.git
cd WeatherAPI

# 2. Create the database (one time)
createuser weather_api --pwprompt
createdb weather_api -O weather_api

# 3. Run the application (Flyway applies migrations automatically)
mvn spring-boot:run
```

The database connection, JWT secret, rate limit and the OpenWeatherMap API key are all configurable via environment variables (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `JWT_EXPIRATION_MINUTES`, `OPENWEATHERMAP_API_KEY`) — the values in `application.yml` are local-development defaults only and must be overridden in any real deployment.

Without `OPENWEATHERMAP_API_KEY` set, the second provider fails with `401` on every real call — that's expected, not a bug: the app keeps working normally because fallback always lands on Open-Meteo. To exercise the second provider for real, grab a [free OpenWeatherMap key](https://openweathermap.org/api) and export it as `OPENWEATHERMAP_API_KEY`.

The API is available at `http://localhost:8080`, with Swagger documentation at `http://localhost:8080/swagger-ui/index.html`.

## ✅ Tests

```bash
mvn test
```

Repository tests and the end-to-end security/fallback tests run against a real PostgreSQL database (`weather_api_test`), not H2, so constraints (unique email, unique favorite per user) are verified the same way they'll behave in production. The circuit breaker test forces a real transition into the `OPEN` state (via WireMock) and confirms the provider stops being called while it's open.

## 📝 Notes

- Open-Meteo's geocoding picks the most relevant result by name; ambiguous city names can return the wrong location (no country/coordinate disambiguation yet).
- Rate limiting, circuit breaker state and cached data are all in-memory and per instance (Caffeine); none of it is shared across multiple application instances yet.
- No favorite-removal endpoint at this stage — list and add only, matching the project's defined scope.
- The original spec sketched `/weather/compare?cities=` for comparing different cities. This project implements `/weather/compare?city=` instead, comparing the **same** city across different providers side by side — that's the feature actually described in the spec's "differentiation" phase, and it's what demonstrates the real value of the multi-provider architecture.

## 📄 License

MIT — see [LICENSE](LICENSE).

---

Developed by **David Arsénio Martins**
🌐 [ividi.dev](https://ividi.dev/) · 💻 [github.com/VidiPT89](https://github.com/VidiPT89/)
