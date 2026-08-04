# Doro ERP Service

Doro ERP의 핵심 업무 규칙을 제공하는 Spring Boot API 프로젝트다. 모듈러 모놀리스 골격이 구성되어 있으며, 실행 모듈 `apps/erp-api`와 공통 Web 기술 모듈 `platform/web`을 중심으로 동작한다. 공통 Testcontainers 지원은 `test-support`로 분리돼 있고, 구조 검증은 `architecture-tests` 프로젝트에서 수행한다. Vue.js 프론트엔드는 별도 `Doro-ERP-Front` 저장소에서 관리한다.

## 현재 상태

- Spring Boot 애플리케이션과 Gradle Wrapper 구성이 완료됐다.
- 멀티프로젝트 등록 상태: `apps/erp-api`, `platform/web`, `modules/identity`, `modules/audit`, `test-support`, `architecture-tests`.
- `platform/web`은 공통 Web/ProblemDetail/Request ID 계약을 제공한다.
- 공통 Testcontainers 지원은 `test-support`로 분리돼 있다.
- `identity`의 계정·인증·Session·Rate Limit·역할/권한·감사 합성 API와 `audit`의 Audit/Privacy 계약·저장 Adapter가 구현됐다.
- Identity 15개 HTTP Handler, Identity 9개 Table과 Audit/Privacy 4개 Table Migration이 등록됐다.

## 기술 구성

| 영역 | 현재 설정 |
|---|---|
| Language | Java 25 (요구) |
| Framework | Spring Boot 4.1.0 |
| Build | Gradle 9.5.1 Wrapper / Kotlin DSL |
| Packaging | JAR |
| 기본 Package | `com.dorosoft.erp` |
| Web | Spring MVC / HTTPS·JSON REST API |
| Security | Spring Security / Stateful Session |
| Session Store | Spring Session Data Redis |
| Persistence | Spring Data JPA / MySQL |
| Migration | Flyway |
| 외부 HTTP | Spring REST Client |
| Observability | Spring Boot Actuator |
| Test | JUnit Jupiter / Spring Boot Test / Testcontainers MySQL·Redis |
| Configuration | YAML |

## 사전 요구사항

- Eclipse Temurin JDK 25 또는 호환되는 JDK 25
- Docker Desktop 또는 호환되는 Docker Engine
- 현재 검증 환경 기본 JDK: 21.0.11
- 현재 검증 환경 docker: 명령 없음 (`command not found`)
- IntelliJ IDEA

Gradle Wrapper를 사용하므로 전역 Gradle 설치는 필요하지 않다.

## 프로젝트 구조

```text
Doro-ERP-Service/
├─ build.gradle.kts
├─ settings.gradle.kts
├─ gradle/wrapper/
├─ gradlew
├─ gradlew.bat
├─ modules/
│  ├─ identity/
│  │  ├─ build.gradle.kts
│  │  └─ src/main/java/com/dorosoft/erp/identity/...
│  └─ audit/
│     ├─ build.gradle.kts
│     └─ src/main/java/com/dorosoft/erp/audit/...
├─ apps/
│  └─ erp-api/
│     ├─ build.gradle.kts
│     └─ src/
│        ├─ main/
│        │  ├─ java/com/dorosoft/erp/
│        │  │  └─ DoroErpServiceApplication.java
│        │  └─ resources/
│        │     ├─ application.yaml
│        │     └─ application-prod.yaml
│        └─ test/
│           └─ java/com/dorosoft/erp/
│              ├─ DoroErpServiceApplicationTests.java
│              └─ TestDoroErpServiceApplication.java
├─ platform/
│  └─ web/
│     ├─ build.gradle.kts
│     └─ src/main/java/com/dorosoft/erp/platform/web/
│        ├─ ApiErrorCode.java
│        ├─ ProblemCode.java
│        ├─ ProblemFieldError.java
│        ├─ ProblemAwareException.java
│        ├─ RequestIdFilter.java
│        └─ GlobalProblemAdvice.java
├─ test-support/
│  ├─ build.gradle.kts
│  └─ src/main/java/com/dorosoft/erp/testsupport/
│     └─ TestcontainersConfiguration.java
└─ architecture-tests/
   └─ src/test/java/com/dorosoft/erp/architecture/Step01BoundaryTest.java
```

## 빌드·실행

```bash
./gradlew projects
./gradlew check
./gradlew :platform:web:test
./gradlew :architecture-tests:test
./gradlew :modules:identity:test
./gradlew :modules:audit:test
./gradlew :apps:erp-api:test
./gradlew :apps:erp-api:bootJar
./gradlew :apps:erp-api:dependencies --configuration runtimeClasspath
./gradlew :apps:erp-api:bootRun
```

Windows PowerShell에서는 `./gradlew` 대신 `.\gradlew.bat`를 사용한다. `:apps:erp-api:test`와 Root `check`의 전체 Context 검증에는 실행 중인 Docker가 필요하다.

로컬 실행은 저장소 Root에서 `.env.example`을 `.env`로 복사한 뒤 Database와 Redis 값을 채우고 실행한다. `.env`는 Spring `Environment`에만 Import되며 Shell 환경 변수를 Export하지 않는다.

```bash
cp .env.example .env
./gradlew :apps:erp-api:bootRun
```

## 설정 원칙

- 기본 설정 파일은 `apps/erp-api/src/main/resources/application.yaml`이다.
- `application.yaml`은 Flyway 위치를 `classpath:db/migration`, Hibernate를 `ddl-auto=validate`로 고정한다.
- 업무 Migration은 `apps/erp-api`가 아니라 각 `modules/<owner>/src/main/resources/db/migration/`이 소유한다.
- 운영 전환 시 `application-prod.yaml`과 타입 안전한 `@ConfigurationProperties`가 HTTPS, 비로컬 Host, Redis TLS·Namespace, Identity 및 Audit/Privacy Key Ring을 Fail-Closed 검증한다.
- Audit Payload HMAC, Audit·Identity Cursor HMAC과 개인정보 접속 주소 AES-GCM Key는 서로 및 Identity Key와 분리하며 운영은 Secrets Manager·ExternalSecret, 개발은 `.env`에서 주입한다.
- Spring Boot BOM이 관리하는 의존성은 개별 버전을 임의로 고정하지 않는다.
- `.env`는 로컬 실행 편의 파일이며 `.env.example`는 실제 Secret 값을 포함하지 않는다.

## 다음 구성 작업

- 후속 업무 모듈 구현(Store, Catalog...)
- OpenAPI 3.1 문서 생성과 검증 구성
- Docker 환경에서 MySQL·Redis Testcontainers 전체 Context와 Flyway Migrate/Validate 검증
- 운영 WAF·ALB Gate, Redis와 Secrets Manager ExternalSecret 연결
