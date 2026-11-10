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
| **payment-service** | 8089 | Payment attempts, mock gateway, and settlement callback delivery |
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
| **Database** | PostgreSQL |
| **Storage** | AWS S3 |
| **Communication** | REST APIs (sync), Kafka (async) |
| **API Docs** | Swagger / OpenAPI |
| **Build** | Maven |

---

## 🚀 Getting Started

### Prerequisites

- Java 17+
- Maven 3.8+
- PostgreSQL 15+
- Apache Kafka
- Docker (optional)

## Authentication and required configuration

Auth Service signs user JWTs with an RSA private key; services and the Gateway
receive only the matching RSA public key for verification. Provide the private
key as PKCS#8 DER encoded in Base64 through `TRUCARE_JWT_PRIVATE_KEY_BASE64`
to Auth Service only. Provide the matching X.509 DER public key as
`TRUCARE_JWT_PUBLIC_KEY_BASE64` to Auth Service, the Gateway, and each
JWT-protected service. Use RSA keys of at least 2048 bits and do not commit the
private key. JWTs use RS256, the configured issuer, a `trucare-api` audience,
and an expiration of 900 seconds by default; `TRUCARE_JWT_ISSUER` and
`TRUCARE_JWT_EXPIRATION_SECONDS` can be configured.

Claim, Policy, and Settlement Feign clients resolve Member, Provider, Policy,
and Claims targets through Eureka service IDs and authenticate with
`TRUCARE_SERVICE_USERNAME` and `TRUCARE_SERVICE_PASSWORD` Basic-auth
credentials. The receiving Member, Provider, Claim, and Policy services grant
that account only the `SERVICE` role. Configure the same service credentials
for those callers and receivers, use strong deployment-specific credentials outside local
development, and protect Basic authentication with TLS in deployed
environments. Payment-to-Settlement retains its separate configured account.
Set `EUREKA_URL` to override the local default
`http://localhost:8761/eureka/`; Feign service calls do not use the public
Gateway.

For local development, database URLs and usernames are configured directly in
each service's `src/main/resources` application properties/YAML files. The
Eureka URL defaults to local Eureka and can be overridden with `EUREKA_URL`.
The root-level `local-secrets.properties` file is
gitignored and supplies the local `spring.datasource.password`; keep it local
and never commit it. Create separate PostgreSQL databases for tests before
running Maven: `trucare_auth_test`, `trucare_members_test`,
`trucare_providers_test`, `trucare_documents_test`, `trucare_claims_test`,
`trucare_care_test`, `trucare_policies_test`, `trucare_payments_test`,
`trucare_settlements_test`,
`trucare_notifications_security_test`, and
`trucare_notifications_persistence_test`. Settlement currently has no
database-backed test suite. Create these databases in PostgreSQL before
running Maven tests. Settlement, Payment, and Notification also retain
their existing HTTP Basic operator/internal account settings; configure the
corresponding environment variables documented in those service sections
below, using distinct strong credentials.

```sql
CREATE DATABASE trucare_auth_test;
CREATE DATABASE trucare_members_test;
CREATE DATABASE trucare_providers_test;
CREATE DATABASE trucare_documents_test;
CREATE DATABASE trucare_claims_test;
CREATE DATABASE trucare_care_test;
CREATE DATABASE trucare_policies_test;
CREATE DATABASE trucare_payments_test;
CREATE DATABASE trucare_settlements_test;
CREATE DATABASE trucare_notifications_security_test;
CREATE DATABASE trucare_notifications_persistence_test;
```

Registration is public and assigns only `PENDING_MEMBER`, which cannot access
member resources. A trusted identity-verification and profile-linking process
must promote an account to `MEMBER` only after confirming the member's identity;
that promotion is currently an operator provisioning step, not an in-app
workflow. Elevated roles must never be granted through public registration.
Login accepts a username or email plus password; send the returned JWT as
`Authorization: Bearer <token>`. JWT validation is performed by the Gateway and
each protected service. Resource services do not receive the JWT signing key.

## Run Locally

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
cd ../payment-service && mvn spring-boot:run

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
| Payment Service | http://localhost:8089/api/payments |

---

## 📡 Key API Endpoints

```
POST   /auth/token                  → Get JWT token (also POST /auth/login)
POST   /auth/register               → Register user (also POST /auth/addUser)

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

POST   /api/payments                → Initiate a payment for a ready settlement
GET    /api/payments/{id}           → Retrieve payment, attempts, and audit history
POST   /api/payments/{id}/retry     → Retry a definitively failed payment

POST   /api/documents/upload        → Upload supporting document
GET    /api/documents/{claimId}     → Get documents for a claim
```

Claim creation requires an `Idempotency-Key` header. Repeating the same key with the
same request returns the original claim; using that key with a different request
returns HTTP 409. Generate one key per logical submission and reuse it for retries.

### Policy Service

The Policy Service uses its own PostgreSQL database. Create it before starting the service:

```sql
CREATE DATABASE trucare_policies;
```

The default connection is `jdbc:postgresql://localhost:5432/trucare_policies`
with the local PostgreSQL username configured in `application.properties`. The
service registers with Eureka on port 8087.

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
independent PostgreSQL database `trucare_settlements`. Create the database before
starting it:

```sql
CREATE DATABASE trucare_settlements;
```

The default database connection and Eureka URL are configured in
`settlement-service/src/main/resources/application.properties`.

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

## Payment Service

Payment Service runs on port 8089 and uses its own PostgreSQL database, `trucare_payments`:

```sql
CREATE DATABASE trucare_payments;
```

The default database connection and Eureka URL are configured in
`payment-service/src/main/resources/application.properties`. Startup requires environment variables
`PAYMENT_OPERATOR_USERNAME`, `PAYMENT_OPERATOR_PASSWORD`,
`PAYMENT_INTERNAL_USERNAME`, `PAYMENT_INTERNAL_PASSWORD`,
`SETTLEMENT_PAYMENT_USERNAME`, and `SETTLEMENT_PAYMENT_PASSWORD`. The first pair
authorizes payment initiation, retry, and queries. The internal pair authorizes
reconciliation and dead-letter callback redrive. Settlement credentials must be
the payment-service account configured for Settlement Service. All use HTTP
Basic for this development version; the authorization policy is a replaceable
component. Use unique strong credentials and HTTPS in deployed environments.
No credentials are included in source or default configuration.

Payment creation accepts only a settlement ID and payment method; amount,
currency, claim, recipient, and settlement status are fetched from Settlement
Service and never trusted from the caller:

```http
POST /api/payments
Authorization: Basic <payment-operator-credentials>
Idempotency-Key: PAYMENT-REQ-12345
Content-Type: application/json
```

```json
{
  "settlementId": 1001,
  "paymentMethod": "BANK_TRANSFER"
}
```

The settlement must be `READY_FOR_PAYMENT`, have a positive payable amount,
supported INR currency, and consistent recipient/settlement types. The mock
gateway is deterministic and defaults to `SUCCESS`; configure
`PAYMENT_GATEWAY_MOCK_RESULT` as `SUCCESS`, `FAILED`, `TIMEOUT`, or `PROCESSING`.
It does not perform transfers. The payment's single logical operation is
uniquely tied to its settlement, while each retry creates a separately
audited transaction attempt. Idempotency keys and canonical request hashes are
stored in PostgreSQL: same key and payload replays the original result, while a
different payload returns HTTP 409.

After marking the settlement `PAYMENT_PROCESSING`, Payment Service invokes the
mock gateway. Success or definitive failure is persisted before the callback to
Settlement Service. Callback deliveries are separately persisted and retried
with bounded exponential backoff; enable or tune them with
`PAYMENT_CALLBACK_RETRY_ENABLED`, `PAYMENT_CALLBACK_RETRY_MAX_ATTEMPTS`,
`PAYMENT_CALLBACK_RETRY_INITIAL_DELAY_MS`, and
`PAYMENT_CALLBACK_RETRY_FIXED_DELAY_MS`, and
`PAYMENT_CALLBACK_RETRY_STALE_TIMEOUT_MS`. Stale in-flight callback claims are
recovered after the configured timeout without duplicating gateway execution.
If a callback fails, the recorded payment result is retained and callback retry
does not execute another payment.
Dead callback deliveries can be redriven through
`POST /api/payments/{id}/callbacks/retry` using internal credentials.

A gateway `TIMEOUT` is recorded as an unknown outcome (`PROCESSING`), not a
failure. `POST /api/payments/{id}/reconcile` queries the provider reference; a
payment cannot be retried until the provider returns a definitive failure.
Payment retry is only allowed after Settlement Service has separately been
returned to `READY_FOR_PAYMENT` by its operator retry workflow. Reconciliation
can be enabled on a schedule with `PAYMENT_RECONCILIATION_ENABLED=true`; it is
disabled by default. Refund status/history are modeled, but refunds are not
implemented in V1; a future refund flow must verify provider settlement and
record a separate transaction before moving a successful payment to `REFUNDED`.

Retrieval/search endpoints include:
`GET /api/payments/reference/{paymentReference}`,
`GET /api/payments/settlement/{settlementId}`, and paginated
`GET /api/payments?status=SUCCESS&settlementId=1001&claimId=CLM1001&memberId=M501&recipientType=PROVIDER&from=2026-01-01T00:00:00Z&to=2026-12-31T23:59:59Z`.
Page size is capped at 100. Payment OpenAPI is available through the Gateway's
unified Swagger UI.

## Notification Service

The Notification Service runs on port `8090` and uses the independent
PostgreSQL `trucare_notifications` database (the PostgreSQL driver is inherited
from the root Maven parent). It accepts internal notification requests,
renders administrator-managed templates, and persists delivery before a
scheduled worker contacts the configured provider. Delivery is asynchronous;
notification failures do not affect claim, settlement, or payment processing.
The service does not resolve or log recipient personal details; internal
callers supply trusted recipient information.

The default PostgreSQL connection and Eureka URL are configured in
`notification-service/src/main/resources/application.properties`. Configure
`NOTIFICATION_INTERNAL_USERNAME`,
`NOTIFICATION_INTERNAL_PASSWORD`, `NOTIFICATION_ADMIN_USERNAME`, and
`NOTIFICATION_ADMIN_PASSWORD`. Credentials are required at startup and must be
unique strong values. V1 follows the environment-configured Basic-auth pattern
behind a replaceable `NotificationSecurityPolicy`; production member access
should bind the authenticated platform identity to the recipient ID.

Create a template as an administrator before dispatching that event/channel:

```http
POST /api/notification-templates
Authorization: Basic <notification-admin-credentials>
Content-Type: application/json
```

```json
{
  "templateCode": "CLAIM-APPROVED-EMAIL",
  "eventType": "CLAIM_APPROVED",
  "channel": "EMAIL",
  "subjectTemplate": "Your healthcare claim has been approved",
  "messageTemplate": "Hello {{memberName}}, claim {{claimId}} has been approved for {{approvedAmount}}.",
  "active": true
}
```

Templates are safe literal substitutions; only `{{identifier}}` placeholders
are recognized, missing variables are rejected, and expressions are never
evaluated. One active template per event and channel is allowed. `PUT
/api/notification-templates/{templateCode}` updates or deactivates it.

Create notifications with an internal service credential and an idempotency
key:

```http
POST /api/notifications
Authorization: Basic <notification-internal-credentials>
Idempotency-Key: CLAIM-CLM1001-APPROVED-EMAIL
Content-Type: application/json
```

```json
{
  "eventType": "CLAIM_APPROVED",
  "recipientId": "501",
  "recipientType": "MEMBER",
  "channel": "EMAIL",
  "recipientAddress": "member@example.com",
  "relatedEntityType": "CLAIM",
  "relatedEntityId": "CLM1001",
  "templateCode": "CLAIM-APPROVED-EMAIL",
  "businessDeduplicationReference": "CLM1001-APPROVED-V1",
  "variables": {
    "memberName": "Ram",
    "claimId": "CLM1001",
    "approvedAmount": "120000"
  }
}
```

Same idempotency key and payload returns the original notification; reusing the
key for another payload returns HTTP 409. The optional business-deduplication
reference suppresses repeat notifications only for the same event, recipient,
and channel. Claim approval/rejection/document requests and settlement/payment
final outcomes are mandatory transactional events and bypass disabled channel
preferences; all other events respect a disabled preference. Unconfigured
preferences default to enabled. Use
`PUT /api/notifications/recipient/{recipientId}/preferences?recipientType=MEMBER`
to update a recipient preference.

`NOTIFICATION_EMAIL_MODE=MOCK` is the default deterministic email provider.
For SMTP set it to `SMTP` and provide `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`,
`SMTP_PASSWORD`, `SMTP_FROM`, `SMTP_AUTH`, and `SMTP_STARTTLS`. SMS is a mock
provider in V1; in-app notifications are persisted and have separate read state.
`NOTIFICATION_DELIVERY_ENABLED`, `NOTIFICATION_DELIVERY_FIXED_DELAY_MS`,
`NOTIFICATION_DELIVERY_BATCH_SIZE`, `NOTIFICATION_DELIVERY_MAX_ATTEMPTS`,
`NOTIFICATION_DELIVERY_INITIAL_DELAY_MS`, and
`NOTIFICATION_STALE_PROCESSING_TIMEOUT_MS` configure the bounded worker. Provider
calls occur outside database transactions. Retries reuse a stable
provider-idempotency reference; SMTP itself does not guarantee exactly-once
delivery after an ambiguous process crash.

Query notifications using `GET /api/notifications/{id}`,
`GET /api/notifications/reference/{reference}`,
`GET /api/notifications/recipient/{recipientId}`, or paginated
`GET /api/notifications?eventType=PAYMENT_SUCCESS&channel=IN_APP&status=SENT&relatedEntityId=PAY1001`.
In-app lists are available at `/api/notifications/in-app/{recipientId}` and
`/unread`; mark a notification read with `PATCH
/api/notifications/{id}/read`. `POST /api/notifications/{id}/retry` retries a
failed notification only while below the bounded attempt limit. Template APIs
are administrator-only; recipient endpoints enforce owner matching unless the
caller has an internal/admin role. The event handler is transport-independent
so a future Kafka adapter can call it without changing delivery logic.

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
├── payment-service/      # Payment attempts and gateway orchestration
├── notification-service/ # Templates and asynchronous notification delivery
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
