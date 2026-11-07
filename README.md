# TrueCare — Healthcare Insurance Claims Platform

> An event-driven microservices platform that automates real-time insurance claims validation, fraud detection, and settlement workflows.

---

## 🩺 Problem Statement

Insurance claims processing is riddled with inefficiencies and fraud. During a personal experience — my mother's hospitalization — I noticed the insurance company was billed for a **single room** while she was actually in a **shared room**. The claim was processed without any real-time cross-validation, leading to overbilling.

**TrueCare** was built to solve exactly this: a system that automatically validates every claim against member eligibility, provider legitimacy, and submitted documents — catching discrepancies before they result in fraudulent payouts.

---

## 🏗️ Architecture

```
                        ┌─────────────────────────────────────────────────┐
                        │              CLIENT (Postman / UI)               │
                        └──────────────────────┬──────────────────────────┘
                                               │ HTTP Request
                                               ▼
                        ┌─────────────────────────────────────────────────┐
                        │                  API GATEWAY                     │
                        │         (Spring Cloud Gateway - Port 8080)       │
                        │   Route → Auth, Claim, Member, Provider, Doc     │
                        └──────────────────────┬──────────────────────────┘
                                               │
                    ┌──────────────────────────▼──────────────────────────┐
                    │                   EUREKA SERVER                      │
                    │              (Service Discovery - Port 8761)         │
                    └──┬───────────┬──────────┬──────────┬────────────────┘
                       │           │          │          │
          ┌────────────▼─┐  ┌──────▼────┐  ┌─▼────────┐ │
          │ AUTH SERVICE  │  │  MEMBER   │  │ PROVIDER  │ │
          │  (Port 8081)  │  │  SERVICE  │  │  SERVICE  │ │
          │  JWT + OAuth2 │  │ (Port 8082)│  │(Port 8083)│ │
          └──────────────┘  └──────┬────┘  └─┬────────┘ │
                                   │          │           │
                        ┌──────────▼──────────▼───────────▼──────────────┐
                        │                 CLAIM SERVICE                    │
                        │                 (Port 8084)                      │
                        │   Creates claim → publishes event to Kafka       │
                        └──────────────────────┬──────────────────────────┘
                                               │ Kafka Event: claim.created
                          ┌────────────────────▼────────────────────┐
                          │              APACHE KAFKA                 │
                          │         (Event Broker - Port 9092)        │
                          └───┬─────────────────────┬───────────────┘
                              │                     │
               ┌──────────────▼──────┐   ┌──────────▼──────────────┐
               │  DOCUMENT SERVICE   │   │      CARE SERVICE        │
               │    (Port 8085)      │   │      (Port 8086)         │
               │  S3 Upload + Rules  │   │  Care Plan Validation    │
               │  Fraud Detection    │   └─────────────────────────┘
               └─────────────────────┘
```

---

## ⚙️ Services Overview

| Service | Port | Responsibility |
|---|---|---|
| **api-gateway** | 8080 | Single entry point, routing, JWT validation |
| **eureka-server** | 8761 | Service discovery and registration |
| **auth-service** | 8081 | User authentication, JWT token generation |
| **member-service** | 8082 | Member eligibility and coverage validation |
| **provider-service** | 8083 | Hospital/provider legitimacy verification |
| **claim-service** | 8084 | Core claim creation and lifecycle management |
| **document-service** | 8085 | Document upload (AWS S3) + fraud rule engine |
| **care-service** | 8086 | Care plan and treatment validation |
| **shared-library** | — | Common DTOs, constants, utilities |

---

## 🔄 Claim Processing Flow

```
1. Client submits claim → API Gateway
2. API Gateway validates JWT → routes to Claim Service
3. Claim Service checks Member Service (Is member eligible?)
4. Claim Service checks Provider Service (Is hospital legitimate?)
5. Claim accepted → Claim Service publishes "claim.created" event to Kafka
6. Document Service consumes event → validates uploaded documents against billing
7. Rule Engine checks:
   ├── Room type match (single vs shared)
   ├── Procedure billed vs documented
   ├── Amount within coverage limits
   └── Duplicate claim detection
8. Care Service consumes event → validates care plan alignment
9. Claim status updated: APPROVED / FLAGGED_FOR_REVIEW / REJECTED
```

---

## 🛡️ Fraud Detection Rules

The Document Service contains a rule-based validation engine that flags suspicious claims:

- **Room Type Mismatch** — Billed for single room but document shows shared room
- **Duplicate Procedures** — Same procedure billed more than once in same claim
- **Amount Threshold Breach** — Billed amount exceeds standard rate by >20%
- **Missing Documents** — Procedure claimed without supporting document
- **Coverage Period Violation** — Claim date outside active coverage period

---

## 🧰 Tech Stack

| Category | Technologies |
|---|---|
| **Backend** | Java 17, Spring Boot 3.x |
| **Microservices** | Spring Cloud, Spring Cloud Gateway |
| **Service Discovery** | Netflix Eureka |
| **Messaging** | Apache Kafka |
| **Security** | Spring Security, JWT, OAuth2 |
| **Database** | MySQL |
| **Storage** | AWS S3 |
| **Communication** | REST APIs (sync), Kafka (async) |
| **API Docs** | Swagger / OpenAPI |
| **Build** | Maven |

---

## 🚀 Getting Started

### Prerequisites

- Java 17+
- Maven 3.8+
- MySQL 8+
- Apache Kafka
- Docker (optional)

### Run Locally

```bash
# 1. Clone the repository
git clone https://github.com/ram-mvrr/trucare.git
cd trucare

# 2. Start Eureka Server first
cd eureka-server && mvn spring-boot:run

# 3. Start Auth Service
cd ../auth-service && mvn spring-boot:run

# 4. Start remaining services (in any order)
cd ../member-service && mvn spring-boot:run
cd ../provider-service && mvn spring-boot:run
cd ../claim-service && mvn spring-boot:run
cd ../document-service && mvn spring-boot:run
cd ../care-service && mvn spring-boot:run

# 5. Start API Gateway last
cd ../api-gateway && mvn spring-boot:run
```

### Service URLs

| Service | URL |
|---|---|
| Eureka Dashboard | http://localhost:8761 |
| API Gateway | http://localhost:8080 |
| Unified Swagger UI | http://localhost:8080/swagger-ui.html |

---

## 📡 Key API Endpoints

```
POST   /api/auth/login              → Get JWT token
POST   /api/auth/register           → Register user

GET    /api/members/{id}            → Get member details
POST   /api/members                 → Register member

GET    /api/providers/{id}          → Get provider details

POST   /api/claims                  → Submit new claim
GET    /api/claims/{id}             → Get claim status
PUT    /api/claims/{id}/status      → Update claim status

POST   /api/documents/upload        → Upload supporting document
GET    /api/documents/{claimId}     → Get documents for a claim
```

---

## 📂 Project Structure

```
trucare/
├── api-gateway/          # Spring Cloud Gateway
├── eureka-server/        # Service discovery
├── auth-service/         # Authentication & JWT
├── member-service/       # Member management
├── provider-service/     # Provider management
├── claim-service/        # Core claims processing
├── document-service/     # Document upload & fraud detection
├── care-service/         # Care plan validation
├── shared-library/       # Common DTOs & utilities
└── pom.xml               # Parent POM
```

---

## 💡 Design Decisions

**Why Kafka for validation?**
Claim validation involves multiple independent services (member, provider, document). Using Kafka allows them to validate in parallel asynchronously rather than sequential blocking REST calls — reducing overall processing time.

**Why a separate Rule Engine in Document Service?**
Rules change frequently (new fraud patterns, regulatory updates). Isolating rule logic in Document Service means rules can be updated without touching claim or member services.

**Why Eureka over Kubernetes DNS?**
For local development and demo simplicity. In production, this would be replaced with Kubernetes service discovery.

---

## 🔮 Roadmap

- [ ] Docker Compose for one-command startup
- [ ] Notification Service (email/SMS on claim status change)
- [ ] Admin dashboard for claims review
- [ ] ML-based fraud scoring (replace rule engine)
- [ ] Deploy to AWS EKS

---

## 👤 Author

**Ramana Reddy MV**
- LinkedIn: [linkedin.com/in/ramana-reddy-malle](https://www.linkedin.com/in/ramana-reddy-malle)
- Email: ram.mvrr@gmail.com
