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
| **policy-service** | 8087 | Policy management, member enrollment, and treatment eligibility |
| **settlement-service** | 8088 | Approved claim settlement calculation, audit, and lifecycle |
| **document-service** | 8085 | Document upload (AWS S3) + fraud rule engine |
| **care-service** | 8086 | Care plan and treatment validation |
| **shared-library** | — | Common DTOs, constants, utilities |

---

## 🔄 Claim Processing Flow

```
1. Client submits claim → API Gateway
2. API Gateway validates JWT → routes to Claim Service
3. Claim Service checks Member Service (Is member eligible?)
4. Claim Service checks Policy Service (policy, enrollment, dates, treatment coverage, and balances)
5. Claim Service checks Provider Service (Is hospital legitimate?)
6. Claim accepted → Claim Service publishes "claim.created" event to Kafka
7. Document Service consumes event → validates uploaded documents against billing
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
cd ../policy-service && mvn spring-boot:run
cd ../settlement-service && mvn spring-boot:run

# 5. Start API Gateway last
cd ../api-gateway && mvn spring-boot:run
```

### Service URLs

| Service | URL |
|---|---|
| Eureka Dashboard | http://localhost:8761 |
| API Gateway | http://localhost:8080 |
| Unified Swagger UI | http://localhost:8080/swagger-ui.html |
| Policy Service | http://localhost:8087/api/policies |
| Settlement Service | http://localhost:8088/api/settlements |

---

## 📡 Key API Endpoints

```
POST   /api/auth/login              → Get JWT token
POST   /api/auth/register           → Register user

GET    /api/members/{id}            → Get member details
POST   /api/members                 → Register member

GET    /api/providers/{id}          → Get provider details
POST   /api/policies                → Create a policy
POST   /api/policies/eligibility/check → Check policy eligibility (read-only)

POST   /api/claims                  → Submit new claim (send a unique Idempotency-Key header)
GET    /api/claims/{id}             → Get claim status
PUT    /api/claims/{id}/status      → Update claim status

POST   /api/settlements             → Create settlement for an approved claim
GET    /api/settlements/{id}        → Retrieve settlement and audit details
POST   /api/settlements/{id}/calculate → Calculate and persist settlement breakdown

POST   /api/documents/upload        → Upload supporting document
GET    /api/documents/{claimId}     → Get documents for a claim
```

Claim creation requires an `Idempotency-Key` header. Repeating the same key with the
same request returns the original claim; using that key with a different request
returns HTTP 409. Generate one key per logical submission and reuse it for retries.

### Policy Service

The Policy Service uses its own MySQL database. Create it before starting the service:

```sql
CREATE DATABASE trucare_policies;
```

Configure credentials using `POLICY_DB_URL`, `POLICY_DB_USERNAME`, and
`POLICY_DB_PASSWORD`. Defaults target `jdbc:mysql://localhost:3306/trucare_policies`
with local `root` and an empty password. The service registers with Eureka on port 8087.

Create a policy and its treatment coverage:

```http
POST /api/policies
Content-Type: application/json
```

```json
{
  "policyNumber": "POL1001",
  "policyName": "Family Health",
  "policyType": "FAMILY_FLOATER",
  "coverageAmount": 500000,
  "startDate": "2026-01-01",
  "endDate": "2026-12-31",
  "status": "ACTIVE",
  "coverages": [
    {
      "treatmentType": "HOSPITALIZATION",
      "coverageLimit": 300000,
      "waitingPeriodDays": 30,
      "description": "In-patient hospital care"
    }
  ]
}
```

Enrollment is a separate operation and validates the member through
`GET /api/members/validate/{memberId}` in Member Service:

```http
POST /api/policies/1001/members
Content-Type: application/json
```

```json
{
  "memberId": "M123456",
  "relationship": "SELF",
  "enrollmentDate": "2026-01-01"
}
```

Check eligibility without reserving or deducting coverage:

```http
POST /api/policies/eligibility/check
Content-Type: application/json
```

```json
{
  "memberId": "M123456",
  "policyNumber": "POL1001",
  "treatmentType": "HOSPITALIZATION",
  "treatmentDate": "2026-11-07",
  "requestedAmount": 150000
}
```

Example eligible response:

```json
{
  "eligible": true,
  "policyId": 1001,
  "policyNumber": "POL1001",
  "memberId": "M123456",
  "coverageAmount": 500000,
  "availableCoverageAmount": 500000,
  "treatmentType": "HOSPITALIZATION",
  "maximumEligibleAmount": 300000,
  "reason": "Policy is active and treatment is covered"
}
```

Ineligible responses use `eligible: false` and provide a reason (for example,
`"Member is not actively enrolled in this policy"`). Eligibility is not claim
approval, and coverage balances are not changed by this read-only check.

Additional endpoints: `GET /api/policies`, `GET /api/policies/{policyId}`,
`PUT /api/policies/{policyId}`, `GET /api/policies/number/{policyNumber}`,
`GET /api/policies/{policyId}/members`, `GET /api/policies/member/{memberId}`,
`DELETE /api/policies/{policyId}/members/{memberId}` (deactivates enrollment), and
`GET /api/policies/{policyId}/coverage`. Lists support `page` and `size` (maximum 100).
The Policy API is included in the unified Swagger UI at
`http://localhost:8080/swagger-ui.html`.

Claim creation now requires the policy and treatment fields so Claim Service can
check eligibility before persisting a claim:

```json
{
  "memberId": "M123456",
  "providerId": "PR123456",
  "policyNumber": "POL1001",
  "treatmentType": "HOSPITALIZATION",
  "treatmentDate": "2026-11-07",
  "claimAmount": 150000
}
```

Send this body to `POST /api/claims` with a unique `Idempotency-Key` header.
An ineligible policy returns HTTP 422; a Policy Service failure returns HTTP 503,
so a dependency outage is not mistaken for a business rejection.

---

## Settlement Service

The Settlement Service listens on port 8088, registers with Eureka, and uses the
independent MySQL database `trucare_settlements`. Create the database before
starting it:

```sql
CREATE DATABASE trucare_settlements;
```

Configure `SETTLEMENT_DB_URL`, `SETTLEMENT_DB_USERNAME`, and
`SETTLEMENT_DB_PASSWORD` as appropriate. For service discovery, set `EUREKA_URL`
if Eureka is not at `http://localhost:8761/eureka/`.

The service requires four environment variables at startup:
`SETTLEMENT_OPERATOR_USERNAME`, `SETTLEMENT_OPERATOR_PASSWORD`,
`SETTLEMENT_PAYMENT_USERNAME`, and `SETTLEMENT_PAYMENT_PASSWORD`. Use unique
strong credentials and HTTPS in deployed environments. Settlement creation,
calculation, cancellation, readiness, and explicit retry require the operator
account. The payment-status callback requires the separate payment-service
account; the environment-backed account provider is isolated behind
`SettlementAccountProvider` so it can later be replaced with service-to-service
authentication. No credentials are committed or provided as defaults.

Create a settlement for a claim that Claim Service reports as `APPROVED`:

```http
POST /api/settlements
Authorization: Basic <settlement-operator-credentials>
Idempotency-Key: settle-CLM1001-2026-01
Content-Type: application/json
```

```json
{
  "claimId": "CLM1001",
  "policyId": 1001,
  "settlementType": "REIMBURSEMENT",
  "deductibleAmount": 5000,
  "copayAmount": 10000,
  "nonPayableAmount": 0
}
```

Settlement Service calls Claim Service's business-ID endpoint
`GET /api/claims/by-claim-id/{claimId}`. The approved amount, member ID, and
provider ID are taken from that response; optional legacy values with those
names in a request are ignored and never become settlement data. The claim
amount must be non-negative and calculations round every input to two decimal
places using `HALF_UP`. The final payable amount is approved amount minus
deductible, co-payment, and non-payable amount.

The idempotency key is stored under a database unique constraint. Repeating the
same key and payload returns the original settlement; reusing it for a different
payload returns HTTP 409. Concurrent duplicate requests are resolved by
returning the winning settlement when the payload matches. A separate unique
claim constraint also prevents more than one settlement per claim.

`policyId` is currently stored as a temporary reference only. It is not
independently verified because Claim Service does not expose a policy ID and
this service does not yet integrate with a trusted Policy Service policy
association.

Calculate, make payment-ready, then report payment lifecycle events (no funds
are transferred by this service):

```http
POST /api/settlements/1/calculate
POST /api/settlements/1/ready-for-payment
POST /api/settlements/1/payment-status
Authorization: Basic <payment-service-credentials>
Content-Type: application/json
```

```json
{
  "status": "PAYMENT_PROCESSING",
  "paymentReference": "PAY10001",
  "remarks": "Payment initiated"
}
```

The payment service may then report `SETTLED` (with a payment reference) or
`FAILED` (with remarks). Payment references and transaction details are stored
in a separate payment-transaction table, and references are unique. A failed
settlement can return to `READY_FOR_PAYMENT` only through the operator-only
`POST /api/settlements/{id}/retry` endpoint. Cancellation is limited to
`PENDING` and `CALCULATED`; all state changes and audit records commit in one
local database transaction.

Additional retrieval and search endpoints:
`GET /api/settlements/number/{settlementNumber}`,
`GET /api/settlements/claim/{claimId}`, and paginated
`GET /api/settlements?status=CALCULATED&type=REIMBURSEMENT&memberId=M501&providerId=P201&from=2026-01-01T00:00:00Z&to=2026-12-31T23:59:59Z`.
Page size is capped at 100. Settlement APIs and OpenAPI documentation are
available through the unified Gateway Swagger UI.

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
├── policy-service/       # Policy enrollment and eligibility
├── settlement-service/   # Settlement calculation and lifecycle
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
