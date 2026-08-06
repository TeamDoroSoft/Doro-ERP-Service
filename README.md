# Doro ERP Service

Doro SaaS POS·Kiosk Backend를 제공하는 Java 25·Spring Boot 4.1 멀티프로젝트다. 하나의 Git 저장소 안에서 Store Access, Commerce, Payment, Queue, Audit을 서로 독립된 Spring Boot Application과 배포 Artifact로 관리한다.

현재 기준선은 MSA 프로젝트 구조, 서비스별 실행·패키징 설정, 데이터 연결 설정 골격과 구조 경계 검증만 제공한다. 업무 API, Domain, Event DTO, Migration과 MongoDB Index 초기화는 구현하지 않았다. 이후 [활성 기능 명세](../Docs/Specifications/README.md)의 Vertical Slice 단위로 각 소유 App에 추가한다. 전환 전 단일 `apps/erp-api`와 `modules/identity`, `modules/store`, `modules/table`, 관계형 `modules/audit` 코드는 제거됐다.

## 실행 단위

| 서비스 | Gradle Project | Package Root | 저장소 | 기본 Port | Image |
|---|---|---|---|---:|---|
| Store Access | `apps:store-access-api` | `com.dorosoft.erp.storeaccess` | PostgreSQL `store_access_db`, Redis | 8081 | `doro-erp-store-access` |
| Commerce | `apps:commerce-api` | `com.dorosoft.erp.commerce` | PostgreSQL `commerce_db` | 8082 | `doro-erp-commerce` |
| Payment | `apps:payment-api` | `com.dorosoft.erp.payment` | PostgreSQL `payment_db` | 8083 | `doro-erp-payment` |
| Queue | `apps:queue-api` | `com.dorosoft.erp.queue` | PostgreSQL `queue_db` | 8084 | `doro-erp-queue` |
| Audit | `apps:audit-api` | `com.dorosoft.erp.audit` | MongoDB `audit.audit_records` | 8085 | `doro-erp-audit` |

App끼리 Gradle Project 의존성을 만들지 않는다. 향후 서비스 간 협력은 HTTP 또는 `platform/messaging-contract`에 추가할 Versioned Event 계약으로 제한한다. 현재 `platform/messaging-contract`와 `platform/observability`는 빈 공통 기술 Project 골격이며 업무 규칙·Entity·Repository를 두지 않는다.

## 프로젝트 구조

```text
Doro-ERP-Service/
├─ apps/
│  ├─ store-access-api/
│  ├─ commerce-api/
│  ├─ payment-api/
│  ├─ queue-api/
│  └─ audit-api/
├─ platform/
│  ├─ web/
│  ├─ observability/
│  └─ messaging-contract/
├─ test-support/
└─ architecture-tests/
```

각 App의 Feature는 `presentation`, `application/api`, `application/port`, `domain`, `infrastructure` 방향으로 구성한다. 다른 App의 Entity, Repository, Migration, Database Credential을 참조하지 않는다.

## 요구사항

- JDK 25
- Git 저장소에 포함된 Gradle 9.5.1 Wrapper

전역 Gradle 설치는 필요하지 않다.

## 빌드와 검증

```bash
./gradlew projects
./gradlew check
./gradlew bootJars
```

서비스별 검증과 실행 JAR 생성은 다음 형식이다.

```bash
./gradlew :apps:store-access-api:test :apps:store-access-api:bootJar
./gradlew :apps:commerce-api:test :apps:commerce-api:bootJar
./gradlew :apps:payment-api:test :apps:payment-api:bootJar
./gradlew :apps:queue-api:test :apps:queue-api:bootJar
./gradlew :apps:audit-api:test :apps:audit-api:bootJar
./gradlew :architecture-tests:test
```

현재 Test는 Gradle·Package·서비스 간 의존성·소유 Database 종류 같은 프로젝트 구조만 검증한다. `test-support`에는 향후 Context Test에서 사용할 PostgreSQL·MongoDB·Redis Testcontainers 의존성만 준비돼 있으며 Container 구성과 Context Test는 구현하지 않았다.

## 로컬 실행

`.env.example`을 복사하고 각 서비스가 소유한 Database Credential만 채운다. `.env`는 커밋하지 않는다.

```bash
cp .env.example .env
./gradlew :apps:store-access-api:bootRun
./gradlew :apps:commerce-api:bootRun
./gradlew :apps:payment-api:bootRun
./gradlew :apps:queue-api:bootRun
./gradlew :apps:audit-api:bootRun
```

각 명령은 별도 Terminal에서 실행한다. App 설정에는 Graceful Shutdown, Actuator Health Probe와 Metrics 기본값이 포함된다. 실제 기동에는 서비스별 Database가 필요하다.

## Migration과 MongoDB Index 자리

- Store Access, Commerce, Payment, Queue는 각 App의 `src/main/resources/db/migration`을 독점 소유한다.
- Hibernate는 Flyway가 만든 Schema를 `ddl-auto=validate`로 검증한다.
- 실행된 Versioned Migration은 수정·삭제·이름 변경하지 않고 새 Forward-fix를 추가한다.
- 현재 Versioned Migration은 없다. 첫 업무 Slice가 새 PostgreSQL 기준선과 `V1`을 함께 정의한다.
- Audit MongoDB Index 초기화도 아직 없다. Audit 기능 Slice에서 Unique·조회·TTL Index와 검증 Test를 함께 추가한다.
- Audit MongoDB는 다른 서비스 업무 상태의 정본이나 복구 저장소로 사용하지 않는다.

## Container Image

Spring Boot Buildpacks를 사용한다. 기본 로컬 Image 이름은 App별 `build.gradle.kts`에 고정돼 있으며 필요하면 Registry 이름과 불변 Tag를 명령에서 지정한다.

```bash
./gradlew :apps:payment-api:bootBuildImage \
  --imageName=ghcr.io/OWNER/doro-erp-payment:GIT_SHA
```

Image와 Build Argument에 Database Credential, Toss Secret, AWS Key를 넣지 않는다.

## 구현 기준

- 기능 정본: [Specifications](../Docs/Specifications/README.md)
- 서비스 경계: [MSA 서비스 컨텍스트](../Docs/Specifications/MSA/README.md)
- 작업·검증 절차: [백엔드 기능 분담 및 AI 코딩 에이전트 작업 가이드](../Docs/백엔드_기능_분담_및_AI_코딩_에이전트_작업_가이드.md)

새 기능은 한 서비스의 한 Vertical Slice로 구현하고 정상 처리뿐 아니라 입력 검증, 권한, 중복, 동시성, 실패·Rollback 중 관련 조건을 Test에 연결한다.
