# 🌦️ Weather API Agregadora

<p align="center">
  <a href="#-português">🇵🇹 Português</a> • <a href="#-english">🇬🇧 English</a>
</p>

---

## 🇵🇹 Português

> Uma API backend em Spring Boot que agrega dados meteorológicos de fontes externas, com cache, autenticação por utilizador e tratamento de erros normalizado — desenhada para continuar a funcionar mesmo quando uma fonte externa falha.

Este repositório contém as **Fases 1, 2 e 3** do projeto: dois providers (Open-Meteo + OpenWeatherMap) com fallback automático e circuit breaker, cache em memória, autenticação JWT, histórico de pesquisas, favoritos, preferências de utilizador, rate limiting e testes automatizados.

### 📦 O que contém

- 🔎 Pesquisa de meteorologia atual por cidade, com normalização de unidades (Celsius/km-h ou Fahrenheit/mph)
- 🧩 Providers desacoplados por uma interface Strategy/Adapter — trocar ou adicionar um provider não exige alterar o controller nem o contrato da API
- 🔁 **Fallback automático entre providers** (Open-Meteo → OpenWeatherMap): se o provider principal falhar, o pedido é servido pelo secundário sem o utilizador reparar
- ⚡ **Circuit breaker + retry com backoff exponencial** (Resilience4j) por provider — um provider a falhar sistematicamente deixa de ser chamado durante uns segundos, evitando sobrecarregá-lo, e falhas transitórias são reabsorvidas por retry antes de se desistir desse provider
- ⚖️ **Endpoint de comparação** (`/weather/compare`) — mostra a mesma cidade lado a lado, servida por todos os providers configurados, com erro por-provider caso algum falhe
- 🌡️ Normalização de unidades entre providers — o OpenWeatherMap devolve Kelvin e m/s nativamente; a conversão para Celsius/Fahrenheit e km/h/mph é feita na aplicação
- ⚡ Cache em memória (Caffeine), TTL configurável, com indicação explícita (`fromCache`) de quando a resposta veio da cache
- 🔐 Autenticação por JWT (registo/login), palavras-passe com hash BCrypt
- 🕘 Histórico de pesquisas e cidades favoritas por utilizador (PostgreSQL, migrações Flyway)
- ⚙️ Preferência de unidades por utilizador — omitir `units` na pesquisa usa a preferência guardada
- 🚧 Rate limiting por utilizador (Bucket4j), configurável, com resposta `429` normalizada
- 🚦 Erros normalizados e nunca expõe o erro cru do provider externo: `404` cidade não encontrada, `502` provider indisponível, `429` quota/rate limit excedido, `400` input inválido, `401` não autenticado, `409` conflito (email/favorito duplicado)
- 🗺️ Descrição do tempo traduzida a partir dos códigos meteorológicos WMO do Open-Meteo (o OpenWeatherMap já devolve a sua própria descrição)
- 📑 Documentação interativa via Swagger/OpenAPI
- ✅ Testes unitários, de integração (WireMock + PostgreSQL real) e ponta-a-ponta (incluindo abertura real do circuit breaker), com ~97% de cobertura de linhas

### 🛠️ Stack Técnica

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

### 🏗️ Estrutura do Projeto

```
weather-api/
├── src/main/java/com/vidi/weather/
│   ├── controller/            # Weather (+ history/favorites/compare), User, Auth
│   ├── service/                # orquestração de cache/provider/fallback, resiliência, utilizadores, histórico, favoritos
│   ├── provider/                # interface Strategy/Adapter + Open-Meteo + OpenWeatherMap
│   ├── security/                # JWT, filtros, rate limiting, UserDetails
│   ├── entity/                  # entidades JPA (User, SearchHistoryEntry, Favorite)
│   ├── repository/              # Spring Data JPA
│   ├── model/                   # domínio interno (imutável)
│   ├── dto/                     # contrato da API (respostas, erros, auth, preferências, comparação)
│   ├── config/                  # cache, RestTemplate, segurança, rate limit, propriedades
│   ├── exception/                # exceções de domínio + handler global
│   └── util/                    # mapeamento de códigos meteorológicos + conversão de unidades
├── src/main/resources/db/migration/  # migrações Flyway
├── src/test/java/                # testes unitários, repositório (Postgres real), WireMock, MockMvc, segurança, fallback/circuit breaker
├── LICENSE                       # MIT License
└── pom.xml
```

### 🌐 Endpoints

```
POST /api/v1/auth/register              — registo (devolve JWT)
POST /api/v1/auth/login                 — login (devolve JWT)

GET  /api/v1/weather?city=&units=       — meteorologia atual, com fallback automático (autenticado)
GET  /api/v1/weather/compare?city=&units= — mesma cidade, lado a lado, em todos os providers
GET  /api/v1/weather/history            — histórico de pesquisas
GET  /api/v1/weather/favorites          — listar favoritos
POST /api/v1/weather/favorites          — adicionar favorito

GET  /api/v1/user/preferences           — preferências do utilizador
POST /api/v1/user/preferences           — atualizar preferências
```

> **Nota:** a spec original sugeria `/weather/compare?cities=` para comparar cidades diferentes. Implementei antes `/weather/compare?city=` para comparar a **mesma** cidade entre providers diferentes, lado a lado — é essa a funcionalidade descrita na secção "Diferenciação" da spec (Fase 3), e é o que demonstra o valor real da arquitetura multi-provider.

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

A ligação à base de dados, o segredo JWT, o limite de rate limiting e a **API key da OpenWeatherMap** são configuráveis via variáveis de ambiente (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `JWT_EXPIRATION_MINUTES`, `OPENWEATHERMAP_API_KEY`) — os valores em `application.yml` são apenas defaults de desenvolvimento local e **devem ser substituídos em qualquer ambiente real**.

⚠️ **Sem `OPENWEATHERMAP_API_KEY` configurada**, o segundo provider falha com `401` em todas as chamadas reais — o que é intencional para efeitos de demonstração: a aplicação continua a funcionar normalmente porque o fallback cai sempre no Open-Meteo. Para testar o segundo provider a valer, cria uma [key gratuita na OpenWeatherMap](https://openweathermap.org/api) e exporta-a como `OPENWEATHERMAP_API_KEY`.

A API fica disponível em `http://localhost:8080` e a documentação Swagger em `http://localhost:8080/swagger-ui/index.html`.

### ✅ Testes

```bash
mvn test
```

Os testes de repositório e os testes de segurança/fallback ponta-a-ponta correm contra uma base de dados PostgreSQL real (`weather_api_test`), não H2 — garante que as constraints (email único, favorito único por utilizador) são validadas tal como em produção. O teste de circuit breaker força mesmo a transição real para o estado `OPEN` (via WireMock) e confirma que o provider deixa de ser chamado enquanto isso acontece.

### 📝 Notas e limitações conhecidas

- O geocoding do Open-Meteo escolhe o resultado mais relevante por nome; nomes ambíguos podem devolver a localidade errada (sem ainda desambiguação por país/coordenadas)
- Rate limiting é em memória por instância (Caffeine); não é partilhado entre múltiplas instâncias da aplicação
- Circuit breaker e estado de fallback também são por instância — em múltiplas instâncias, cada uma decide independentemente se um provider está saudável
- Sem endpoint de remoção de favoritos nesta fase (apenas listar e adicionar, conforme o âmbito definido)
- Sem `OPENWEATHERMAP_API_KEY` configurada, o compare endpoint mostra sempre o OpenWeatherMap como indisponível — é o comportamento esperado, não um bug

---

## 🇬🇧 English

> A Spring Boot backend API that aggregates weather data from external sources, with caching, per-user authentication and normalized error handling — designed to keep working even when an external source fails.

This repository holds **Phases 1, 2 and 3** of the project: two providers (Open-Meteo + OpenWeatherMap) with automatic fallback and a circuit breaker, in-memory caching, JWT authentication, search history, favorites, user preferences, rate limiting and automated tests.

### 📦 What's Inside

- 🔎 Current weather lookup by city, with unit normalization (Celsius/km-h or Fahrenheit/mph)
- 🧩 Providers decoupled behind a Strategy/Adapter interface — swapping or adding a provider never touches the controller or the API contract
- 🔁 **Automatic fallback between providers** (Open-Meteo → OpenWeatherMap): if the primary provider fails, the request is served by the secondary one transparently
- ⚡ **Circuit breaker + retry with exponential backoff** (Resilience4j) per provider — a provider that's systematically failing stops being called for a few seconds, avoiding pile-on load, and transient errors are absorbed by retry before giving up on that provider
- ⚖️ **Comparison endpoint** (`/weather/compare`) — shows the same city side by side across every configured provider, with a per-provider error entry if one fails
- 🌡️ Unit normalization across providers — OpenWeatherMap natively returns Kelvin and m/s; the conversion to Celsius/Fahrenheit and km/h/mph happens in the application
- ⚡ In-memory cache (Caffeine), configurable TTL, with an explicit `fromCache` flag showing whether a response came from cache
- 🔐 JWT authentication (register/login), passwords hashed with BCrypt
- 🕘 Per-user search history and favorite cities (PostgreSQL, Flyway migrations)
- ⚙️ Per-user unit preference — omitting `units` on a search falls back to the saved preference
- 🚧 Per-user rate limiting (Bucket4j), configurable, with a normalized `429` response
- 🚦 Normalized errors that never leak the raw external provider error: `404` city not found, `502` provider unavailable, `429` quota/rate limit exceeded, `400` invalid input, `401` unauthenticated, `409` conflict (duplicate email/favorite)
- 🗺️ Weather descriptions translated from Open-Meteo's WMO weather codes (OpenWeatherMap already returns its own description)
- 📑 Interactive API documentation via Swagger/OpenAPI
- ✅ Unit, integration (WireMock + a real PostgreSQL instance) and end-to-end tests (including a real circuit breaker trip), at ~97% line coverage

### 🛠️ Tech Stack

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

### 🏗️ Project Structure

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
├── LICENSE                       # MIT License
└── pom.xml
```

### 🌐 Endpoints

```
POST /api/v1/auth/register              — register (returns a JWT)
POST /api/v1/auth/login                 — log in (returns a JWT)

GET  /api/v1/weather?city=&units=       — current weather, with automatic fallback (authenticated)
GET  /api/v1/weather/compare?city=&units= — same city, side by side, across every provider
GET  /api/v1/weather/history            — search history
GET  /api/v1/weather/favorites          — list favorites
POST /api/v1/weather/favorites          — add a favorite

GET  /api/v1/user/preferences           — get preferences
POST /api/v1/user/preferences           — update preferences
```

> **Note:** the original spec sketched `/weather/compare?cities=` for comparing different cities. I implemented `/weather/compare?city=` instead, comparing the **same** city across different providers side by side — that's the feature actually described in the spec's "Differentiation" section (Phase 3), and it's what demonstrates the real value of the multi-provider architecture.

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

The database connection, JWT secret, rate limit and the **OpenWeatherMap API key** are all configurable via environment variables (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `JWT_EXPIRATION_MINUTES`, `OPENWEATHERMAP_API_KEY`) — the values in `application.yml` are local-development defaults only and **must be overridden in any real deployment**.

⚠️ **Without `OPENWEATHERMAP_API_KEY` set**, the second provider fails with `401` on every real call — this is intentional for demo purposes: the app keeps working normally because fallback always lands on Open-Meteo. To exercise the second provider for real, grab a [free OpenWeatherMap key](https://openweathermap.org/api) and export it as `OPENWEATHERMAP_API_KEY`.

The API is available at `http://localhost:8080`, with Swagger documentation at `http://localhost:8080/swagger-ui/index.html`.

### ✅ Tests

```bash
mvn test
```

Repository tests and the end-to-end security/fallback tests run against a real PostgreSQL database (`weather_api_test`), not H2 — this ensures constraints (unique email, unique favorite per user) are verified the same way they'll behave in production. The circuit breaker test forces a real transition into the `OPEN` state (via WireMock) and confirms the provider stops being called while it's open.

### 📝 Notes & Known Limitations

- Open-Meteo's geocoding picks the most relevant result by name; ambiguous city names can return the wrong location (no country/coordinate disambiguation yet)
- Rate limiting is in-memory per instance (Caffeine); it isn't shared across multiple application instances
- Circuit breaker and fallback state are also per instance — with multiple instances, each one independently decides whether a provider is healthy
- No favorite-removal endpoint at this stage (list and add only, matching the defined scope)
- Without `OPENWEATHERMAP_API_KEY` set, the compare endpoint always shows OpenWeatherMap as unavailable — that's expected, not a bug

---

Developed by **David Arsénio Martins** — *"Vidi"*

🌐 [ividi.dev](https://ividi.dev/) · 🐙 [GitHub @VidiPT89](https://github.com/VidiPT89/)
