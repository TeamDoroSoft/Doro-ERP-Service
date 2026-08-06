# Doro ERP Service 구현 가이드

## 1. 적용 범위와 기준

이 파일은 `Doro-ERP-Service/` 이하의 모든 작업에 적용되는 백엔드 구현 전용 지침이다. 이 저장소만 내려받아 `Docs/` 디렉터리가 없는 환경에서도 구현 방향을 판단할 수 있어야 한다.

실제 코드와 Manifest가 항상 최우선 기준이다. 작업을 시작할 때 다음 파일과 현재 Git 상태를 먼저 확인한다.

```bash
git status --short --branch
sed -n '1,220p' settings.gradle.kts
sed -n '1,260p' build.gradle.kts
sed -n '1,260p' README.md
rg --files apps platform test-support architecture-tests
```

README나 이 지침과 실제 Gradle·소스 구조가 다르면 임의로 한쪽을 선택하지 않는다. 차이, 구현 영향과 필요한 결정을 먼저 보고한다.

## 2. 선택적 Docs 탐색

`Docs`가 없다고 가정하되 실제 작업공간에 존재할 수 있으므로 작업 시작 시 한 번은 확인한다.

```bash
find .. -maxdepth 2 -type d -name Docs -print
```

발견한 모든 `Docs`가 제품 문서 저장소라고 가정하지 않는다. `Specifications/README.md` 존재 여부로 Doro 제품 문서인지 확인한다. 일반적인 형제 저장소 경로는 `../Docs`다.

제품 문서가 있으면 다음 순서로 작업에 필요한 활성 문서만 읽는다.

1. `Specifications/README.md`
2. `Specifications/MSA/`의 담당 서비스 문서
3. 담당 기능의 `기능 명세.md`와 `기술 설계.md`
4. 복잡한 장애·동시성 작업이면 `테스트 및 운영.md`
5. 필요할 때만 백엔드 작업 가이드와 의사결정 문서

`Archive/`는 현재 구현의 정본이 아니다. 활성 문서와 실제 코드에서 설명되지 않는 과거 결정의 배경을 확인할 때만 사용한다.

제품 문서가 없어도 이 파일, Service README, 실제 Manifest와 요청에 명시된 완료 조건으로 작업을 계속한다. 다만 금액 계산, 상태 전이, 공개 API·Event 호환성처럼 결과를 크게 바꾸는 업무 규칙이 빠져 있으면 추측해 구현하지 말고 필요한 결정을 요청한다. 문서 변경은 구현 작업에 자동 포함하지 않으며 요청 범위에 있거나 공개 계약 변경에 대한 명시적 승인이 있을 때만 수행한다.

## 3. 고정 프로젝트 구조

이 저장소는 하나의 Git Repository에서 다섯 개의 독립 Spring Boot Application을 관리하는 Gradle 멀티프로젝트다.

| 서비스 | Gradle Project | Package Root | 데이터 저장소 | 기본 Port |
|---|---|---|---|---:|
| Store Access | `apps:store-access-api` | `com.dorosoft.erp.storeaccess` | PostgreSQL `store_access_db`, Redis Session | 8081 |
| Commerce | `apps:commerce-api` | `com.dorosoft.erp.commerce` | PostgreSQL `commerce_db` | 8082 |
| Payment | `apps:payment-api` | `com.dorosoft.erp.payment` | PostgreSQL `payment_db` | 8083 |
| Queue | `apps:queue-api` | `com.dorosoft.erp.queue` | PostgreSQL `queue_db` | 8084 |
| Audit | `apps:audit-api` | `com.dorosoft.erp.audit` | MongoDB `audit.audit_records` | 8085 |

보조 Project의 책임은 다음으로 제한한다.

- `platform:web`: 공통 HTTP 오류 계약, Request ID와 Web 기술
- `platform:observability`: Actuator, Metrics와 기술 관측성 기반
- `platform:messaging-contract`: 버전이 있는 Event Envelope와 DTO
- `test-support`: 여러 서비스가 재사용하는 Testcontainers·테스트 도구
- `architecture-tests`: 서비스·계층·데이터 소유권 경계 검증

Root Project는 집계와 공통 Gradle 설정만 담당한다. 루트 `src/`에는 구현 코드를 추가하지 않는다. 새 업무 코드는 반드시 소유 `apps/<service>/src` 아래에 둔다.

기존 다섯 서비스를 더 세분화한 실행 App이나 별도 Repository를 임의로 추가하지 않는다. `Checkout`, `Inventory`, `Integration`, `Notification`, `Logging`, `Kiosk`를 별도 Backend Service로 만들지 않는다. POS와 Kiosk는 같은 Backend API를 사용하고 Channel·Actor Context로 구분한다.

## 4. 기능과 데이터 소유권

| 서비스 | 소유 기능과 데이터 |
|---|---|
| Store Access | 업체·매장, 직원 계정·Role, Kiosk 기기, 기본 Table Master, 직원 Session |
| Commerce | Category·Product, POS·Kiosk 주문, Order History, Payment Projection, 매출·일일 마감 |
| Payment | Toss Test 결제 생성·승인·전액 취소, Payment Operation·History |
| Queue | 입장 `ENTRY`와 음식 준비 `FULFILLMENT` 대기열, Counter·History |
| Audit | 각 서비스 Audit Event의 MongoDB 중앙 조회 문서와 Retention |

다음 경계를 항상 지킨다.

- Store Access의 Table은 기본 정보만 소유한다. 사용 중 여부나 현재 주문 상태를 별도 Table 상태로 만들지 않는다.
- Commerce는 주문에 `tableId`와 필요한 Snapshot을 저장하지만 Store Access Table을 복제하지 않는다.
- Commerce의 Payment Projection은 주문·매출 수렴용이며 Payment 정본이 아니다.
- Payment는 Commerce Order를 논리 ID로 참조하고 `commerce_db`를 직접 읽지 않는다.
- Queue의 `FULFILLMENT`는 Commerce Event로 생성·취소하며 Queue가 Order 정본을 소유하지 않는다.
- Domain History는 각 업무 서비스 Database에 남긴다. Audit MongoDB는 복구용 업무 정본이 아니다.
- 서비스 간 물리 Foreign Key, Database Join, Repository 공유와 다른 서비스 Credential 사용을 금지한다.

## 5. 패키지와 의존 방향

각 App은 현재 Architecture Test가 검사하는 다음 최상위 계층을 유지한다.

```text
com.dorosoft.erp.<service>/
├─ presentation/
├─ application/
│  ├─ api/
│  └─ port/
├─ domain/
└─ infrastructure/
```

서비스 안에 여러 기능이 있으면 각 계층 아래에 `catalog`, `order`, `sales` 같은 기능 하위 패키지를 둔다. 같은 기능의 파일이 과도하게 흩어지지 않도록 한 Vertical Slice의 이름을 일관되게 사용한다.

- `presentation`은 HTTP·Message 입력을 해석하고 Application Use Case를 호출한다.
- `application`은 Use Case, Transaction 경계와 Port를 소유하며 `presentation`·`infrastructure`에 의존하지 않는다.
- `domain`은 상태와 업무 규칙을 소유하며 다른 계층과 Framework에 의존하지 않는다.
- `infrastructure`는 PostgreSQL·MongoDB·Redis·SQS·Toss·HTTP Adapter를 구현하고 `presentation`에 의존하지 않는다.
- Controller와 Consumer가 Repository를 직접 호출하지 않게 한다.
- App끼리 Gradle `project(...)` 의존성을 추가하지 않는다.
- 공통 Platform에는 업무 Entity, Repository, Use Case와 특정 서비스 설정을 넣지 않는다.

Domain에서는 Spring, Jakarta Persistence, MongoDB, Redis, AWS SDK와 Toss SDK 타입을 사용하지 않는다. 외부 시스템의 응답 형식은 Infrastructure Adapter에서 내부 모델로 변환한다.

## 6. 서비스 간 API와 Event

허용되는 주요 동기 호출은 다음과 같다.

- Commerce → Store Access: Store·Table·Timezone Context 확인
- Payment → Commerce: Order ID·서버 계산 금액·결제 가능 상태 확인
- Commerce → Queue: 주문 완료 전 Fulfillment 상태 확인

주요 비동기 Event와 Consumer Queue는 다음과 같다.

| Queue | Consumer | 대표 Event |
|---|---|---|
| `commerce-events.fifo` | Commerce | `PaymentApproved`, `PaymentCancelled` |
| `queue-events.fifo` | Queue | `OrderAccepted`, `OrderCancelled` |
| `audit-events.fifo` | Audit | Store Access·Commerce·Payment·Queue Audit Event |

Event에는 최소한 `eventId`, `eventType`, `eventVersion`, `occurredAt`, Tenant·Store Scope, Aggregate ID와 필요한 최소 Payload를 둔다. Consumer가 모르는 추가 필드를 허용하고 파괴적 변경은 새 Version으로 추가한다. 공유 `messaging-contract` Project를 사용하더라도 구버전 DTO를 즉시 삭제해 Producer와 Consumer의 동시 배포를 강제하지 않는다.

서비스 간 호출을 분산 Transaction으로 묶지 않는다. 동기 호출의 Timeout·오류 계약을 명시하고, Event 수렴이 가능한 흐름은 Outbox와 Consumer 멱등성으로 복구한다.

## 7. Transaction, Outbox와 SQS

- 업무 상태, Domain History와 발행 Outbox는 소유 PostgreSQL의 같은 Local Transaction에 저장한다.
- Producer는 Outbox를 Claim한 뒤 Database Lock을 해제하고 SQS로 전송한다.
- SQS 전송 성공 후 상태 기록 전에 종료될 수 있으므로 중복 전달을 정상 상황으로 취급한다.
- PostgreSQL Consumer는 `consumer_name + event_id` Inbox Unique Constraint와 업무 반영을 같은 Transaction에 둔다.
- Audit Consumer는 `sourceService + eventId` MongoDB Unique Index로 중복을 제거한다.
- FIFO Queue도 Exactly-once 업무 처리를 보장한다고 가정하지 않는다.
- `MessageGroupId`는 필요한 Aggregate 순서를 돕는 Scope로 선택하고 전역 순서를 기대하지 않는다.
- 실패 Message는 제한된 재시도 후 DLQ로 이동하며 원 Event ID를 유지한 채 재처리한다.
- SQS나 외부 HTTP 호출 중 Database Row Lock을 유지하지 않는다.

AWS가 관리형 Outbox Publisher를 제공한다고 가정하지 않는다. Publisher와 Claim·Lease·재시도 로직은 이를 소유한 App의 Application Port와 Infrastructure Adapter로 구현한다.

## 8. 멱등성과 동시성

재시도 가능한 Command API는 Operation별 `Idempotency-Key` 계약을 사용한다.

- Client는 최초 요청에서 Key를 만들고 같은 Operation의 재시도에만 같은 Key를 재사용한다.
- 주문 생성, Payment 생성, 승인과 취소는 서로 다른 Operation이므로 각각 다른 Key를 사용한다.
- 서버는 원문 Key를 저장하지 않고 Key Digest, Operation Scope, Request HMAC과 재생 가능한 결과를 저장한다.
- 같은 Key·같은 Operation·같은 Payload는 이전 결과를 재생한다.
- 같은 Key에 다른 Payload가 오면 외부 호출이나 상태 변경 전에 `409 Conflict`로 거절한다.
- Application의 선조회만 믿지 않고 Unique Constraint로 최종 경쟁을 막는다.
- `Idempotency-Key`는 사용자 인증 수단이나 MITM 방어 수단이 아니다. 인증·TLS·인가를 별도로 적용한다.

Aggregate 상태 변경은 허용된 Domain 전이와 `version` 또는 조건부 Update로 보호한다. 번호 발급은 Tenant·Store·영업일 Scope의 Counter와 Unique Constraint를 사용한다. 동시성 Test는 실제 PostgreSQL Container에서 실행해 Database Constraint와 Lock 동작을 검증한다.

## 9. Payment 구현 불변 규칙

- Front는 Commerce에 Order를 생성한 뒤 Payment에 `orderId`로 Payment 생성을 요청한다.
- Payment는 Toss 호출 전에 Commerce API로 Order ID, 서버 금액과 결제 가능 상태를 확인한다.
- 한 Order에는 유효한 `PENDING` Payment 하나만 허용한다.
- Payment 생성, 승인과 취소는 별도 멱등 Operation이다.
- 승인·취소 Operation을 짧은 Transaction에서 Claim하고 Lock을 해제한 뒤 Toss Test API를 호출한다.
- 결과 반영 Transaction에서 Claim과 현재 상태를 다시 확인하고 Payment·History·Outbox를 함께 Commit한다.
- Timeout이나 응답 유실처럼 Provider 반영 여부가 불명확하면 성공이나 실패로 추정하지 않고 `REVIEW_REQUIRED`로 남긴다.
- 결제 승인·취소 결과는 Commerce Event로 전달하며 Payment가 Order·Queue 상태를 직접 변경하지 않는다.
- MVP에서는 Toss Test Key, 전액 취소만 사용한다. 운영 Key, 부분 취소와 Webhook을 임의로 추가하지 않는다.

## 10. Database와 Migration

Store Access, Commerce, Payment와 Queue는 각 App의 `src/main/resources/db/migration`을 독점 소유한다.

- Flyway가 Schema를 생성하고 Hibernate는 `ddl-auto=validate`로 검증한다.
- 실행된 Versioned Migration은 수정·삭제·이름 변경하지 않고 새 Forward-fix를 추가한다.
- 모든 Tenant 소유 Row에 `tenant_id`와 필요한 `store_id` Scope를 둔다.
- 서비스 내부 FK는 허용하지만 서비스 간 ID는 논리 참조로 저장한다.
- Idempotency, 번호 Counter, Inbox와 업무상 단일 관계에는 Database Unique Constraint를 둔다.
- Migration과 Entity 변경은 깨끗한 PostgreSQL에서 Migrate·Validate하는 Test를 포함한다.

Audit는 MongoDB Document 하나에 Audit Event 하나를 저장한다.

- `sourceService + eventId` Unique Index를 둔다.
- Tenant·기간·Action·Target 조회 Index를 명시적으로 관리한다.
- `expiresAt` TTL Index를 사용하되 TTL 삭제가 비동기임을 고려해 조회에서도 만료 문서를 제외한다.
- Document·Index 변경은 반복 실행 가능한 초기화 또는 버전이 있는 Migration과 검증 Test를 포함한다.

관계형 Database에 MySQL Driver·Dialect·Testcontainer를 다시 추가하지 않는다.

## 11. API, 인증과 보안

- 직원은 `OWNER`, `MANAGER`, `STAFF` Role만 사용하며 별도 Permission Aggregate로 확장하지 않는다.
- Store Access가 직원 Session과 Kiosk 기기 자격증명의 정본이다.
- 다른 서비스가 전달된 Actor Context를 무조건 신뢰하지 않도록 서비스 자격증명과 위조 방지 계약을 둔다.
- Tenant·Store Scope는 Controller 입력만이 아니라 Use Case와 Repository Query에도 적용한다.
- 공개 오류는 `platform:web`의 Problem 계약과 Request ID를 사용하고 내부 예외·SQL·Secret을 노출하지 않는다.
- 비밀번호는 강한 단방향 Hash만 저장하고 원문·복호화 가능한 형태를 남기지 않는다.
- Session ID, Cookie, CSRF·Authorization Header, Kiosk Secret, Toss Secret, `paymentKey`, 개인정보와 원문 `Idempotency-Key`를 Log·Audit·Event에 남기지 않는다.
- Secret에는 안전한 기본값을 두지 않고 운영 설정은 누락 시 Fail-Closed한다.
- Toss Secret은 Payment Runtime에만, 각 Database Credential은 소유 서비스에만 주입한다.
- AWS 인증정보를 소스나 환경 예제에 넣지 않고 Default Credential Provider Chain과 IAM Role을 사용한다.

Actuator의 상세 정보와 Metrics endpoint는 공개 API와 같은 Port에서 무제한 노출하지 않는다. Network Policy, Management Port 또는 인증 정책이 아직 없다면 운영 준비 완료로 판단하지 않는다.

## 12. Logging, History와 Audit

기술 Logging, Domain History와 중앙 Audit을 구분한다.

- 기술 Logging: 각 App에서 Request ID·Trace ID, 안전한 식별자, 결과와 지연을 구조화해 기록
- Domain History: 소유 Aggregate 상태 전이의 정본 이력으로 같은 업무 Transaction에 저장
- Audit Outbox: 중요 행위를 중앙 Audit로 전달하기 위한 발행 원본
- Central Audit: MongoDB의 Tenant 범위 통합 조회 모델

업무 Use Case가 중앙 Audit API를 동기 호출하지 않는다. Use Case는 서비스 내부 Audit Recorder 또는 Port를 호출하고, Adapter가 같은 Transaction의 Outbox에 최소 Allowlist Payload를 저장한다. Audit 전송·적재 실패로 이미 Commit된 원 업무를 Rollback하지 않는다.

## 13. 설정과 의존성

- Java 25, 저장소의 Gradle Wrapper와 Kotlin DSL을 사용한다.
- Spring Boot BOM이 관리하는 의존성 버전을 개별 App에서 임의로 고정하지 않는다.
- 새 Library는 표준 Spring 기능이나 현재 의존성으로 해결할 수 없는 이유가 있을 때만 추가한다.
- 서비스별 `application.yaml`, 환경 변수와 타입 안전한 `@ConfigurationProperties`를 함께 관리한다.
- 값이 채워진 `.env`, Secret, 개인 인증정보와 운영 Key를 커밋하지 않는다.
- `.env.example`에는 변수 이름과 안전한 로컬 예제만 둔다.
- 서비스별 실행 JAR과 Image 이름을 유지하고 공통 Root 실행 JAR을 만들지 않는다.

## 14. 테스트와 완료 조건

한 Vertical Slice는 정상 처리만으로 완료되지 않는다. 관련되는 다음 항목을 자동화 Test로 검증한다.

- 입력 경계, Tenant·Store Scope, Role과 기기 인증
- Domain 상태 전이와 잘못된 전이 거절
- 동일 멱등 Key 재시도와 같은 Key·다른 Payload 충돌
- 실제 PostgreSQL Unique Constraint·Lock·동시 요청
- Local Transaction Rollback과 History·Outbox·Inbox 원자성
- Event 중복·역순·지연, Consumer 재시작과 DLQ 경로
- 외부 HTTP·Toss Adapter의 성공, 거절, Timeout과 불명확 결과
- MongoDB Unique·조회·TTL Index
- Flyway Migrate·Validate 또는 MongoDB Index 초기화
- 전체 Spring Context와 서비스 간 금지 의존성

Test 이름은 구현 Method가 아니라 관찰 가능한 동작을 설명한다. Mock만으로 Database Constraint, Transaction, 직렬화와 외부 Adapter 경계를 통과했다고 판단하지 않는다. PostgreSQL·MongoDB·Redis 통합 검증에는 `test-support`의 Testcontainers 구성을 사용한다.

공통 검증 명령은 다음과 같다.

```bash
./gradlew check
./gradlew bootJars
```

서비스별 작업에서는 담당 App Test와 JAR을 먼저 검증하고 마지막에 Architecture Test와 Root 검증을 수행한다.

```bash
./gradlew :apps:<service>:test :apps:<service>:bootJar
./gradlew :architecture-tests:test
./gradlew check
```

실제로 존재하지 않거나 실행하지 못한 Test를 통과했다고 보고하지 않는다. 실패하면 명령, 핵심 원인과 검증하지 못한 위험을 명확히 남긴다.

## 15. 구현 작업 절차

1. Git 상태와 다른 작업자의 변경을 확인한다.
2. Root·Service 지침, 실제 Manifest와 대상 코드·테스트를 읽는다.
3. 선택적 `Docs` 경로를 찾고 존재하면 담당 활성 명세만 확인한다.
4. 요청을 한 서비스의 한 API 또는 한 Event 처리 Vertical Slice로 한정한다.
5. 소유 Aggregate, Database, 동기 API와 Event 계약, 실패 경계를 명시한다.
6. 관찰 가능한 정상·실패·중복·동시성 Test를 구현과 함께 추가한다.
7. Domain부터 Application Port, Adapter, Presentation 방향으로 연결한다.
8. Migration·설정·보안·관측성 영향을 같은 Slice에서 확인한다.
9. 담당 App, Architecture Test, Root `check`와 필요한 JAR을 검증한다.
10. 변경 파일, 동작 결과, 검증 명령과 남은 교차 서비스 작업을 보고한다.

여러 서비스가 관여하는 흐름도 한 번에 모두 구현하지 않는다. Event 계약, Producer, Consumer와 E2E를 검토 가능한 Slice로 나누되 임시 공유 Repository나 다른 서비스 Database 접근으로 연결하지 않는다.

## 16. Git과 변경 보호

- 이 저장소의 사용자 변경과 미추적 파일을 소유권 확인 없이 되돌리거나 삭제하지 않는다.
- `git reset --hard`, 무분별한 `git clean`, 강제 Checkout과 Migration 재작성 같은 파괴적 작업을 하지 않는다.
- Commit, Push, Branch 생성과 이력 재작성은 사용자가 명시적으로 요청했을 때만 수행한다.
- Root Gradle, 공통 Platform과 공개 Event 계약 변경은 모든 서비스 영향을 확인한다.
- 변경 범위 밖의 포맷 변경과 기계적 대량 수정은 피한다.
- API·Event·Database 계약을 바꿨지만 선택적 Docs가 없거나 문서 수정이 범위 밖이면 필요한 후속 문서 변경을 완료 보고에 남긴다.

## 17. 완료 보고

완료 보고는 다음을 간결하게 포함한다.

- 구현된 관찰 가능 동작과 담당 서비스
- 주요 변경 파일과 Migration·설정·계약 영향
- 실행한 검증 명령과 실제 결과
- 재시도·동시성·보안·외부 장애 중 확인한 항목
- 실행하지 못한 검증과 남은 위험
- 다른 서비스나 선택적 Docs에 필요한 후속 작업

코드가 컴파일된다는 이유만으로 기능, 데이터 수렴과 운영 준비가 완료됐다고 표현하지 않는다.
