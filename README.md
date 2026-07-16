# 🌦️ Weather API Agregadora

<p align="center">
  <a href="#-português">🇵🇹 Português</a> • <a href="#-english">🇬🇧 English</a>
</p>

---

## 🇵🇹 Português

> Uma API backend em Spring Boot que agrega dados meteorológicos de fontes externas, com cache, autenticação por utilizador e tratamento de erros normalizado — desenhada para continuar a funcionar mesmo quando uma fonte externa falha.

Este repositório contém as **Fases 1 e 2** do projeto: um provider (Open-Meteo), cache em memória, autenticação JWT, histórico de pesquisas, favoritos, preferências de utilizador, rate limiting e testes automatizados. A Fase 3 (2º provider, fallback automático, circuit breaker) está planeada mas ainda não implementada.

### 📦 O que contém

- 🔎 Pesquisa de meteorologia atual por cidade, com normalização de unidades (Celsius/km-h ou Fahrenheit/mph)
- 🧩 Providers desacoplados por uma interface Strategy/Adapter — trocar ou adicionar um provider não exige alterar o controller nem o contrato da API
- ⚡ Cache em memória (Caffeine), TTL configurável, com indicação explícita (`fromCache`) de quando a resposta veio da cache
- 🔐 Autenticação por JWT (registo/login), palavras-passe com hash BCrypt
- 🕘 Histórico de pesquisas e cidades favoritas por utilizador (PostgreSQL, migrações Flyway)
- ⚙️ Preferência de unidades por utilizador — omitir `units` na pesquisa usa a preferência guardada
- 🚧 Rate limiting por utilizador (Bucket4j), configurável, com resposta `429` normalizada
- 🚦 Erros normalizados e nunca expõe o erro cru do provider externo: `404` cidade não encontrada, `502` provider indisponível, `429` quota/rate limit excedido, `400` input inválido, `401` não autenticado, `409` conflito (email/favorito duplicado)
- 🗺️ Descrição do tempo traduzida a partir dos códigos meteorológicos WMO do Open-Meteo
- 📑 Documentação interativa via Swagger/OpenAPI
- ✅ Testes unitários, de integração (WireMock + PostgreSQL real) e de segurança ponta-a-ponta, com ~97% de cobertura de linhas

### 🛠️ Stack Técnica

![Java](https://img.shields.io/badge/Java%2021-ED8B00?style=flat&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot%203-6DB33F?style=flat&logo=springboot&logoColor=white)
![Spring Security](https://img.shields.io/badge/Spring%20Security-6DB33F?style=flat&logo=springsecurity&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-4169E1?style=flat&logo=postgresql&logoColor=white)
![Flyway](https://img.shields.io/badge/Flyway-CC0200?style=flat&logo=flyway&logoColor=white)
![JWT](https://img.shields.io/badge/JWT-000000?style=flat&logo=jsonwebtokens&logoColor=white)
![Maven](https://img.shields.io/badge/Maven-C71A36?style=flat&logo=apachemaven&logoColor=white)
![Caffeine](https://img.shields.io/badge/Caffeine%20Cache-6F4E37?style=flat)
![Bucket4j](https://img.shields.io/badge/Bucket4j-2E7D32?style=flat)
![JUnit5](https://img.shields.io/badge/JUnit%205-25A162?style=flat&logo=junit5&logoColor=white)
![WireMock](https://img.shields.io/badge/WireMock-3E863D?style=flat)
![OpenAPI](https://img.shields.io/badge/OpenAPI%20%2F%20Swagger-85EA2D?style=flat&logo=swagger&logoColor=black)

### 🏗️ Estrutura do Projeto

```
weather-api/
├── src/main/java/com/vidi/weather/
│   ├── controller/            # Weather (+ history/favorites), User, Auth
│   ├── service/                # orquestração de cache/provider, utilizadores, histórico, favoritos
│   ├── provider/                # interface Strategy/Adapter + Open-Meteo
│   ├── security/                # JWT, filtros, rate limiting, UserDetails
│   ├── entity/                  # entidades JPA (User, SearchHistoryEntry, Favorite)
│   ├── repository/              # Spring Data JPA
│   ├── model/                   # domínio interno (imutável)
│   ├── dto/                     # contrato da API (respostas, erros, auth, preferências)
│   ├── config/                  # cache, RestTemplate, segurança, rate limit, propriedades
│   ├── exception/                # exceções de domínio + handler global
│   └── util/                    # mapeamento de códigos meteorológicos
├── src/main/resources/db/migration/  # migrações Flyway
├── src/test/java/                # testes unitários, repositório (Postgres real), WireMock, MockMvc, segurança
├── LICENSE                       # MIT License
└── pom.xml
```

### 🌐 Endpoints

```
POST /api/v1/auth/register              — registo (devolve JWT)
POST /api/v1/auth/login                 — login (devolve JWT)

GET  /api/v1/weather?city=&units=       — meteorologia atual (autenticado)
GET  /api/v1/weather/history            — histórico de pesquisas
GET  /api/v1/weather/favorites          — listar favoritos
POST /api/v1/weather/favorites          — adicionar favorito

GET  /api/v1/user/preferences           — preferências do utilizador
POST /api/v1/user/preferences           — atualizar preferências
```

| Cenário | Status |
|---|---|
| Sucesso | `200` / `201` |
| Não autenticado | `401` |
| Cidade não encontrada | `404` |
| Conflito (email/favorito duplicado) | `409` |
| Provider externo indisponível | `502` |
| Quota do provider ou rate limit excedido | `429` |
| Parâmetro inválido ou em falta | `400` |

### 🚀 Como correr

Pré-requisitos: Java 21, Maven e PostgreSQL a correr localmente.

```bash
# 1. Clonar o repositório
git clone https://github.com/VidiPT89/WeatherAPI.git
cd WeatherAPI

# 2. Criar a base de dados (uma vez)
createuser weather_api --pwprompt
createdb weather_api -O weather_api

# 3. Correr a aplicação (Flyway aplica as migrações automaticamente)
mvn spring-boot:run
```

A ligação à base de dados, o segredo JWT e o limite de rate limiting são configuráveis via variáveis de ambiente (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `JWT_EXPIRATION_MINUTES`) — os valores em `application.yml` são apenas defaults de desenvolvimento local e **devem ser substituídos em qualquer ambiente real**.

A API fica disponível em `http://localhost:8080` e a documentação Swagger em `http://localhost:8080/swagger-ui/index.html`.

### ✅ Testes

```bash
mvn test
```

Os testes de repositório e o teste de segurança ponta-a-ponta correm contra uma base de dados PostgreSQL real (`weather_api_test`), não H2 — garante que as constraints (email único, favorito único por utilizador) são validadas tal como em produção.

### 📝 Notas e limitações conhecidas

- O geocoding do Open-Meteo escolhe o resultado mais relevante por nome; nomes ambíguos podem devolver a localidade errada (sem ainda desambiguação por país/coordenadas)
- Um único provider nesta fase — sem fallback real nem circuit breaker ainda (Fase 3)
- Rate limiting é em memória por instância (Caffeine); não é partilhado entre múltiplas instâncias da aplicação
- Sem endpoint de remoção de favoritos nesta fase (apenas listar e adicionar, conforme o âmbito definido)

---

## 🇬🇧 English

> A Spring Boot backend API that aggregates weather data from external sources, with caching, per-user authentication and normalized error handling — designed to keep working even when an external source fails.

This repository holds **Phases 1 and 2** of the project: one provider (Open-Meteo), in-memory caching, JWT authentication, search history, favorites, user preferences, rate limiting and automated tests. Phase 3 (a second provider, automatic fallback, circuit breaker) is planned but not yet implemented.

### 📦 What's Inside

- 🔎 Current weather lookup by city, with unit normalization (Celsius/km-h or Fahrenheit/mph)
- 🧩 Providers decoupled behind a Strategy/Adapter interface — swapping or adding a provider never touches the controller or the API contract
- ⚡ In-memory cache (Caffeine), configurable TTL, with an explicit `fromCache` flag showing whether a response came from cache
- 🔐 JWT authentication (register/login), passwords hashed with BCrypt
- 🕘 Per-user search history and favorite cities (PostgreSQL, Flyway migrations)
- ⚙️ Per-user unit preference — omitting `units` on a search falls back to the saved preference
- 🚧 Per-user rate limiting (Bucket4j), configurable, with a normalized `429` response
- 🚦 Normalized errors that never leak the raw external provider error: `404` city not found, `502` provider unavailable, `429` quota/rate limit exceeded, `400` invalid input, `401` unauthenticated, `409` conflict (duplicate email/favorite)
- 🗺️ Weather descriptions translated from Open-Meteo's WMO weather codes
- 📑 Interactive API documentation via Swagger/OpenAPI
- ✅ Unit, integration (WireMock + a real PostgreSQL instance) and end-to-end security tests, at ~97% line coverage

### 🛠️ Tech Stack

![Java](https://img.shields.io/badge/Java%2021-ED8B00?style=flat&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot%203-6DB33F?style=flat&logo=springboot&logoColor=white)
![Spring Security](https://img.shields.io/badge/Spring%20Security-6DB33F?style=flat&logo=springsecurity&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-4169E1?style=flat&logo=postgresql&logoColor=white)
![Flyway](https://img.shields.io/badge/Flyway-CC0200?style=flat&logo=flyway&logoColor=white)
![JWT](https://img.shields.io/badge/JWT-000000?style=flat&logo=jsonwebtokens&logoColor=white)
![Maven](https://img.shields.io/badge/Maven-C71A36?style=flat&logo=apachemaven&logoColor=white)
![Caffeine](https://img.shields.io/badge/Caffeine%20Cache-6F4E37?style=flat)
![Bucket4j](https://img.shields.io/badge/Bucket4j-2E7D32?style=flat)
![JUnit5](https://img.shields.io/badge/JUnit%205-25A162?style=flat&logo=junit5&logoColor=white)
![WireMock](https://img.shields.io/badge/WireMock-3E863D?style=flat)
![OpenAPI](https://img.shields.io/badge/OpenAPI%20%2F%20Swagger-85EA2D?style=flat&logo=swagger&logoColor=black)

### 🏗️ Project Structure

```
weather-api/
├── src/main/java/com/vidi/weather/
│   ├── controller/            # Weather (+ history/favorites), User, Auth
│   ├── service/                 # cache/provider orchestration, users, history, favorites
│   ├── provider/                # Strategy/Adapter interface + Open-Meteo
│   ├── security/                # JWT, filters, rate limiting, UserDetails
│   ├── entity/                  # JPA entities (User, SearchHistoryEntry, Favorite)
│   ├── repository/              # Spring Data JPA
│   ├── model/                   # internal domain (immutable)
│   ├── dto/                     # API contract (responses, errors, auth, preferences)
│   ├── config/                  # cache, RestTemplate, security, rate limit, properties
│   ├── exception/                # domain exceptions + global handler
│   └── util/                    # weather code mapping
├── src/main/resources/db/migration/  # Flyway migrations
├── src/test/java/                # unit, repository (real Postgres), WireMock, MockMvc, security tests
├── LICENSE                       # MIT License
└── pom.xml
```

### 🌐 Endpoints

```
POST /api/v1/auth/register              — register (returns a JWT)
POST /api/v1/auth/login                 — log in (returns a JWT)

GET  /api/v1/weather?city=&units=       — current weather (authenticated)
GET  /api/v1/weather/history            — search history
GET  /api/v1/weather/favorites          — list favorites
POST /api/v1/weather/favorites          — add a favorite

GET  /api/v1/user/preferences           — get preferences
POST /api/v1/user/preferences           — update preferences
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

### 🚀 How to Run

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

The database connection, JWT secret and rate limit are all configurable via environment variables (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `JWT_EXPIRATION_MINUTES`) — the values in `application.yml` are local-development defaults only and **must be overridden in any real deployment**.

The API is available at `http://localhost:8080`, with Swagger documentation at `http://localhost:8080/swagger-ui/index.html`.

### ✅ Tests

```bash
mvn test
```

Repository tests and the end-to-end security test run against a real PostgreSQL database (`weather_api_test`), not H2 — this ensures constraints (unique email, unique favorite per user) are verified the same way they'll behave in production.

### 📝 Notes & Known Limitations

- Open-Meteo's geocoding picks the most relevant result by name; ambiguous city names can return the wrong location (no country/coordinate disambiguation yet)
- Single provider at this stage — no real fallback or circuit breaker yet (Phase 3)
- Rate limiting is in-memory per instance (Caffeine); it isn't shared across multiple application instances
- No favorite-removal endpoint at this stage (list and add only, matching the defined scope)

---

Developed by **David Arsénio Martins** — *"Vidi"*

🌐 [ividi.dev](https://ividi.dev/) · 🐙 [GitHub @VidiPT89](https://github.com/VidiPT89/)
