# Teknisk dokumentation

## Projektnamn

**Projekt:**

**Version:**

**Datum:**

**Författare:**

---

# Innehållsförteckning

1. Introduktion
2. Systemöversikt
3. Arkitektur
4. Teknikstack
5. Projektstruktur
6. Backend
7. Frontend
8. Databas
9. API-dokumentation
10. Säkerhet
11. Installation
12. Konfiguration
13. Testning
14. Deployment
15. Versionshantering
16. Framtida utveckling

---

# 1. Introduktion

## Syfte

Beskriv projektets syfte.

Exempel:

LIA-praktikanterna genomför sin LIA i ett gemensamt produktionsprojekt: att planera, bygga,
driftsätta och dokumentera en webbaserad lärportal på uppdrag av Högsbo Säljkonsulter AB.
Projektet är valt för att det naturligt engagerar båda utbildningarnas kompetenser och kräver ett
äkta samarbete där molninfrastrukturen och applikationen är beroende av varandra.

## Funktionalitet

Beskriv kort vad användaren kan göra.

Exempel:

* Skapa objekt
* Läsa objekt
* Uppdatera objekt
* Ta bort objekt
* Logga in
* Söka
* Filtrera

---

# 2. Systemöversikt

## Arkitektur

```text
+---------------------+
|      Frontend       |
|     React/Vue       |
+----------+----------+
           |
        REST API
           |
+----------v----------+
|      Backend        |
|    Spring Boot      |
+----------+----------+
           |
       Hibernate/JPA
           |
+----------v----------+
|      MySQL          |
+---------------------+
```

## Kommunikationsflöde

1. Frontend skickar HTTP-request.
2. Backend tar emot request.
3. Service-lagret behandlar logiken.
4. Repository kommunicerar med databasen.
5. Response returneras som JSON.

---

# 3. Arkitektur

## Arkitekturmodell

* MVC
* Layered Architecture
* Clean Architecture

(Beskriv vilken som används.)

## Lager

### Controller

Ansvarar för HTTP-endpoints.

### Service

Affärslogik.

### Repository

Databasåtkomst.

### Entity

Representerar databastabeller.

### DTO

Objekt för kommunikation mellan klient och server.

---

# 4. Teknikstack

| Komponent         | Teknik    |
| ----------------- |-----------|
| Frontend          | React     |
| Backend           | Java      |
| Databas           | Azure SQL |
| ORM               |           |
| Säkerhet          |           |
| Build Tool        |           |
| Dokumentation     |           |
| Versionshantering | Git       |
| Containerisering  |           |

---

# 5. Projektstruktur

## Backend

```text
src
├── config
├── controller
├── dto
├── event
├── exception
├── listener
├── model
├── producer
├── repository
├── service
└── worker
```

### Beskrivning

| Paket      | Beskrivning |
| ---------- | ----------- |
| controller |             |
| service    |             |
| repository |             |
| entity     |             |
| dto        |             |
| config     |             |
| exception  |             |

---

## Frontend

```text
src
├── components
├── pages
├── services
├── hooks
├── context
├── assets
├── styles
└── App.jsx
```

### Beskrivning

| Mapp       | Beskrivning |
| ---------- | ----------- |
| components |             |
| pages      |             |
| services   |             |
| hooks      |             |
| context    |             |
| assets     |             |

---

# 6. Backend

## Lagerstruktur

### Controller

Ansvar:

*

### Service

Ansvar:

*

### Repository

Ansvar:

*

### Entity

Ansvar:

*

---

## Designmönster

* Dependency Injection
* Repository Pattern
* Service Layer
* DTO Pattern

Beskriv hur dessa används.

---

# 7. Frontend

## Arkitektur

Beskriv frontendens struktur.

## Routing

Beskriv hur routing fungerar.

## Komponenter

| Komponent | Beskrivning |
| --------- | ----------- |
|           |             |
|           |             |
|           |             |

## State Management

Beskriv hur state hanteras.

Exempel:

* React Context
* Redux
* Zustand
* useState
* useReducer

## API-kommunikation

Beskriv hur frontend kommunicerar med backend.

---

# 8. Databas

## Databasmodell

Beskriv databasen.

### Tabeller

| Tabell | Beskrivning |
| ------ | ----------- |
|        |             |
|        |             |

### Relationer

* OneToMany
* ManyToOne
* ManyToMany

Beskriv relationerna.

---

## ER-diagram

```text
Customer

id
name
email

      1
Customer -------- Order
             *

Order

id
date
total
```

---

# 9. API-dokumentation

## GET

### GET /api/example

Beskrivning:

Returnerar alla objekt.

### Response

```json
[
    {
        "id":1,
        "name":"Example"
    }
]
```

Statuskod:

200 OK

---

## GET by ID

### GET /api/example/{id}

Response

```json
{
    "id":1,
    "name":"Example"
}
```

Statuskod:

200 OK

404 Not Found

---

## POST

### POST /api/example

Request

```json
{
    "name":"Example"
}
```

Response

```json
{
    "id":1,
    "name":"Example"
}
```

Statuskod:

201 Created

400 Bad Request

---

## PUT

### PUT /api/example/{id}

Request

```json
{
    "name":"Updated"
}
```

Response

```json
{
    "id":1,
    "name":"Updated"
}
```

Statuskod:

200 OK

404 Not Found

---

## DELETE

### DELETE /api/example/{id}

Statuskod:

204 No Content

404 Not Found

---

# 10. Säkerhet

## Autentisering

Beskriv autentiseringsmetoden.

Exempel:

* JWT
* OAuth2
* Sessions

## Auktorisering

Beskriv roller och behörigheter.

## Kryptering

Beskriv lösenordshantering.

## Säkerhetsåtgärder

* CSRF
* XSS
* SQL Injection
* Inputvalidering

---

# 11. Installation

## Klona projektet

```bash
git clone repository-url

cd project
```

---

## Backend

```bash
mvn clean install

mvn spring-boot:run
```

---

## Frontend

```bash
npm install

npm run dev
```

---

## Docker

```bash
docker compose up --build
```

---

# 12. Konfiguration

## Backend

```properties
spring.datasource.url=

spring.datasource.username=

spring.datasource.password=
```

## Frontend

```env
VITE_API_URL=
```

Beskriv varje konfigurationsparameter.

---

# 13. Testning

## Backend

* Unit Tests
* Integration Tests

Kör tester:

```bash
mvn test
```

---

## Frontend

* Component Tests
* E2E Tests

Kör tester:

```bash
npm test
```

---

# 14. Deployment

Beskriv hur applikationen distribueras.

## Exempelarkitektur

```text
                Internet

                    |

               Reverse Proxy

                    |

         +----------------------+

         |      Frontend        |

         +----------------------+

                    |

         +----------------------+

         |      Backend         |

         +----------------------+

                    |

         +----------------------+

         |      Database        |

         +----------------------+
```

---

# 15. Versionshantering

## Branch-struktur

* main
* develop
* feature/*
* bugfix/*

## Arbetsflöde

1. Skapa feature branch
2. Implementera funktion
3. Commit
4. Push
5. Pull Request
6. Code Review
7. Merge

---

# 16. Framtida utveckling

Planerade förbättringar:

* [ ]
* [ ]
* [ ]
* [ ]
* [ ]

---

# Bilagor

## Swagger/OpenAPI

Länk:

---

## UML-diagram

Lägg in klassdiagram.

---

## Sekvensdiagram

Lägg in sekvensdiagram.

---

## ER-diagram

Lägg in databasschema.

---

## Skärmbilder

Lägg in bilder från frontend.

---

## Docker

Beskriv Docker-konfigurationen.

---

## Licens

Beskriv projektets licens.

---

## Kontakt

Ansvarig utvecklare:

E-post:

GitHub:
