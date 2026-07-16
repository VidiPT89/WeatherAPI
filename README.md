# 🌦️ Weather API Agregadora

<p align="center">
  <a href="#-português">🇵🇹 Português</a> • <a href="#-english">🇬🇧 English</a>
</p>

---

## 🇵🇹 Português

> Uma API backend em Spring Boot que agrega dados meteorológicos de fontes externas, com cache, tratamento de erros normalizado e uma arquitetura pronta para fallback entre providers — desenhada para continuar a funcionar mesmo quando uma fonte externa falha.

Este repositório contém a **Fase 1 (MVP)** do projeto: um único provider (Open-Meteo), cache em memória, erros normalizados e testes automatizados. As fases seguintes (autenticação, histórico, favoritos, fallback entre providers, circuit breaker) estão planeadas mas ainda não implementadas — a Fase 1 foi propositadamente fechada e testada antes de se avançar.

### 📦 O que contém

- 🔎 Pesquisa de meteorologia atual por cidade, com normalização de unidades (Celsius/km-h ou Fahrenheit/mph)
- 🧩 Providers desacoplados por uma interface Strategy/Adapter — trocar ou adicionar um provider não exige alterar o controller nem o contrato da API
- ⚡ Cache em memória (Caffeine), TTL configurável, com indicação explícita (`fromCache`) de quando a resposta veio da cache
- 🚦 Erros normalizados e nunca expõe o erro cru do provider externo: `404` cidade não encontrada, `502` provider indisponível, `429` quota excedida, `400` input inválido
- 🗺️ Descrição do tempo traduzida a partir dos códigos meteorológicos WMO do Open-Meteo
- 📑 Documentação interativa via Swagger/OpenAPI
- ✅ Testes unitários, de integração (WireMock, simulando sucesso/falha/timeout do provider) e de contrato HTTP, com ~95% de cobertura de linhas

### 🛠️ Stack Técnica

![Java](https://img.shields.io/badge/Java%2021-ED8B00?style=flat&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot%203-6DB33F?style=flat&logo=springboot&logoColor=white)
![Maven](https://img.shields.io/badge/Maven-C71A36?style=flat&logo=apachemaven&logoColor=white)
![Caffeine](https://img.shields.io/badge/Caffeine%20Cache-6F4E37?style=flat)
![JUnit5](https://img.shields.io/badge/JUnit%205-25A162?style=flat&logo=junit5&logoColor=white)
![WireMock](https://img.shields.io/badge/WireMock-3E863D?style=flat)
![OpenAPI](https://img.shields.io/badge/OpenAPI%20%2F%20Swagger-85EA2D?style=flat&logo=swagger&logoColor=black)

### 🏗️ Estrutura do Projeto

```
weather-api/
├── src/main/java/com/vidi/weather/
│   ├── controller/WeatherController.java     # GET /api/v1/weather
│   ├── service/                              # orquestração de cache + provider
│   ├── provider/                             # interface Strategy/Adapter + Open-Meteo
│   ├── model/                                # domínio interno (imutável)
│   ├── dto/                                  # contrato da API (resposta e erro)
│   ├── config/                               # cache, RestTemplate, propriedades
│   ├── exception/                            # exceções de domínio + handler global
│   └── util/                                 # mapeamento de códigos meteorológicos
├── src/test/java/                            # testes unitários, WireMock e MockMvc
├── LICENSE                                   # MIT License
└── pom.xml
```

### 🌐 Endpoint

```
GET /api/v1/weather?city={cidade}&units={metric|imperial}
```

| Cenário | Status |
|---|---|
| Sucesso | `200` |
| Cidade não encontrada | `404` |
| Provider externo indisponível | `502` |
| Quota do provider excedida | `429` |
| Parâmetro inválido ou em falta | `400` |

### 🚀 Como correr

Pré-requisitos: Java 21 e Maven.

```bash
# 1. Clonar o repositório
git clone https://github.com/VidiPT89/WeatherAPI.git
cd WeatherAPI

# 2. Correr a aplicação
mvn spring-boot:run
```

A API fica disponível em `http://localhost:8080` e a documentação Swagger em `http://localhost:8080/swagger-ui/index.html`.

### ✅ Testes

```bash
mvn test
```

### 📝 Notas e limitações conhecidas

- O geocoding do Open-Meteo escolhe o resultado mais relevante por nome; nomes ambíguos podem devolver a localidade errada (sem ainda desambiguação por país/coordenadas)
- Um único provider nesta fase — sem fallback real nem circuit breaker ainda
- Sem persistência, utilizadores, histórico ou rate limiting próprio nesta fase

---

## 🇬🇧 English

> A Spring Boot backend API that aggregates weather data from external sources, with caching, normalized error handling and an architecture ready for automatic fallback between providers — designed to keep working even when an external source fails.

This repository holds **Phase 1 (MVP)** of the project: a single provider (Open-Meteo), in-memory caching, normalized errors and automated tests. The following phases (authentication, history, favorites, provider fallback, circuit breaker) are planned but not yet implemented — Phase 1 was deliberately closed out and tested before moving on.

### 📦 What's Inside

- 🔎 Current weather lookup by city, with unit normalization (Celsius/km-h or Fahrenheit/mph)
- 🧩 Providers decoupled behind a Strategy/Adapter interface — swapping or adding a provider never touches the controller or the API contract
- ⚡ In-memory cache (Caffeine), configurable TTL, with an explicit `fromCache` flag showing whether a response came from cache
- 🚦 Normalized errors that never leak the raw external provider error: `404` city not found, `502` provider unavailable, `429` quota exceeded, `400` invalid input
- 🗺️ Weather descriptions translated from Open-Meteo's WMO weather codes
- 📑 Interactive API documentation via Swagger/OpenAPI
- ✅ Unit, integration (WireMock, simulating provider success/failure/timeout) and HTTP contract tests, at ~95% line coverage

### 🛠️ Tech Stack

![Java](https://img.shields.io/badge/Java%2021-ED8B00?style=flat&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot%203-6DB33F?style=flat&logo=springboot&logoColor=white)
![Maven](https://img.shields.io/badge/Maven-C71A36?style=flat&logo=apachemaven&logoColor=white)
![Caffeine](https://img.shields.io/badge/Caffeine%20Cache-6F4E37?style=flat)
![JUnit5](https://img.shields.io/badge/JUnit%205-25A162?style=flat&logo=junit5&logoColor=white)
![WireMock](https://img.shields.io/badge/WireMock-3E863D?style=flat)
![OpenAPI](https://img.shields.io/badge/OpenAPI%20%2F%20Swagger-85EA2D?style=flat&logo=swagger&logoColor=black)

### 🏗️ Project Structure

```
weather-api/
├── src/main/java/com/vidi/weather/
│   ├── controller/WeatherController.java     # GET /api/v1/weather
│   ├── service/                              # cache + provider orchestration
│   ├── provider/                              # Strategy/Adapter interface + Open-Meteo
│   ├── model/                                 # internal domain (immutable)
│   ├── dto/                                   # API contract (response and error)
│   ├── config/                                # cache, RestTemplate, properties
│   ├── exception/                              # domain exceptions + global handler
│   └── util/                                  # weather code mapping
├── src/test/java/                             # unit, WireMock and MockMvc tests
├── LICENSE                                    # MIT License
└── pom.xml
```

### 🌐 Endpoint

```
GET /api/v1/weather?city={city}&units={metric|imperial}
```

| Scenario | Status |
|---|---|
| Success | `200` |
| City not found | `404` |
| External provider unavailable | `502` |
| Provider quota exceeded | `429` |
| Invalid or missing parameter | `400` |

### 🚀 How to Run

Prerequisites: Java 21 and Maven.

```bash
# 1. Clone the repository
git clone https://github.com/VidiPT89/WeatherAPI.git
cd WeatherAPI

# 2. Run the application
mvn spring-boot:run
```

The API is available at `http://localhost:8080`, with Swagger documentation at `http://localhost:8080/swagger-ui/index.html`.

### ✅ Tests

```bash
mvn test
```

### 📝 Notes & Known Limitations

- Open-Meteo's geocoding picks the most relevant result by name; ambiguous city names can return the wrong location (no country/coordinate disambiguation yet)
- Single provider at this stage — no real fallback or circuit breaker yet
- No persistence, users, history or own rate limiting at this stage

---

Developed by **David Arsénio Martins** — *"Vidi"*

🌐 [ividi.dev](https://ividi.dev/) · 🐙 [GitHub @VidiPT89](https://github.com/VidiPT89/)
