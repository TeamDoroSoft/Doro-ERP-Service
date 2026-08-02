# Doro ERP Service

Doro ERP의 핵심 업무 규칙을 제공하는 Spring Boot API 프로젝트다. 현재는 Spring Initializr로 생성한 애플리케이션 기반 단계이며, 기능 구현 전 모듈러 모놀리스 구조와 공통 개발 환경을 구성한다. Vue.js 프론트엔드는 별도 `Doro-ERP-Front` 저장소에서 관리한다.

## 현재 상태

- Spring Boot 애플리케이션과 Gradle Wrapper 구성이 완료됐다.
- 일반 실행에는 외부 MySQL과 Redis 연결 정보가 필요하다.
- 개발 실행과 통합 테스트에서는 Testcontainers로 MySQL과 Redis를 실행할 수 있다.
- 업무 모듈, API, Entity, Repository와 Flyway Migration은 아직 구현되지 않았다.
- 인증은 OAuth2/JWT Resource Server가 아닌 Spring Security와 Redis 기반 Stateful Session을 사용한다.
- Amazon SQS와 외부 알림 Provider 종속성은 알림 기능 구현 시 별도로 추가한다.

## 기술 구성

| 영역 | 현재 설정 |
|---|---|
| Language | Java 25 |
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

주요 런타임 종속성은 다음과 같다.

- Spring Boot Actuator
- Spring Data JPA
- Spring Flyway와 Flyway MySQL
- Spring REST Client
- Spring Security
- Spring Session Data Redis
- Spring Validation
- Spring Web MVC
- MySQL Connector/J

## 사전 요구사항

- Eclipse Temurin JDK 25 또는 호환되는 JDK 25
- Docker Desktop 또는 호환되는 Docker Engine
- IntelliJ IDEA

전역 Gradle 설치는 필요하지 않다. 저장소에 포함된 Gradle Wrapper를 사용한다.

IntelliJ에서는 다음 값을 확인한다.

```text
Project SDK: JDK 25
Gradle JVM: JDK 25
Gradle Distribution: Wrapper
File Encoding: UTF-8
```

## 프로젝트 구조

```text
.
├─ build.gradle.kts
├─ settings.gradle.kts
├─ gradle/wrapper/
├─ gradlew
├─ gradlew.bat
└─ src/
   ├─ main/
   │  ├─ java/com/dorosoft/erp/
   │  │  └─ DoroErpServiceApplication.java
   │  └─ resources/
   │     └─ application.yaml
   └─ test/
      └─ java/com/dorosoft/erp/
         ├─ DoroErpServiceApplicationTests.java
         ├─ TestDoroErpServiceApplication.java
         └─ TestcontainersConfiguration.java
```

현재는 단일 Gradle 프로젝트다. 본격적인 기능 개발 전에는 `Docs`에 확정된 업무 경계와 의존 방향에 맞춰 모듈 구조를 구성한다.

## 개발 환경 실행

### Testcontainers로 실행

로컬에 MySQL과 Redis를 직접 설치하지 않는 기본 개발 방식이다.

1. Docker Desktop 또는 Docker Engine을 실행한다.
2. IntelliJ에서 `src/test/java/com/dorosoft/erp/TestDoroErpServiceApplication.java`를 연다.
3. `TestDoroErpServiceApplication.main()`을 실행한다.

`TestcontainersConfiguration`이 MySQL과 Redis Container를 시작하고 `@ServiceConnection`으로 애플리케이션 연결 정보를 주입한다. 현재 Container 이미지는 `mysql:latest`, `redis:latest`이므로 운영 버전이 확정되면 같은 버전의 명시적인 태그로 고정해야 한다.

### 외부 MySQL과 Redis로 실행

일반 `DoroErpServiceApplication` 또는 `bootRun`을 실행하려면 다음 환경변수를 설정한다.

| 환경변수 | 설명 | 예시 |
|---|---|---|
| `SPRING_DATASOURCE_URL` | MySQL JDBC URL | `jdbc:mysql://localhost:3306/doro_erp` |
| `SPRING_DATASOURCE_USERNAME` | MySQL 사용자 | `doro` |
| `SPRING_DATASOURCE_PASSWORD` | MySQL 비밀번호 | 로컬 시크릿 |
| `SPRING_DATA_REDIS_HOST` | Redis Host | `localhost` |
| `SPRING_DATA_REDIS_PORT` | Redis Port | `6379` |

JPA와 Flyway가 활성화되어 있으므로 MySQL 연결 정보가 없으면 애플리케이션은 시작되지 않는다. Redis 연결정보도 Session 기능을 사용하기 전에 구성해야 한다. 비밀번호와 운영 인증정보는 `application.yaml`이나 Git에 커밋하지 않는다.

## 빌드와 테스트

Windows PowerShell에서는 다음 명령어를 사용한다.

```powershell
.\gradlew.bat clean test
.\gradlew.bat check
.\gradlew.bat build
.\gradlew.bat bootRun
```

macOS와 Linux에서는 다음 명령어를 사용한다.

```bash
./gradlew clean test
./gradlew check
./gradlew build
./gradlew bootRun
```

| 명령 | 목적 |
|---|---|
| `clean test` | 기존 Build 결과를 제거하고 테스트 실행 |
| `check` | 테스트를 포함한 Gradle 검증 수행 |
| `build` | 검증 후 실행 가능한 JAR 생성 |
| `bootRun` | 외부 MySQL·Redis 설정으로 애플리케이션 실행 |

Testcontainers 기반 테스트에는 실행 중인 Docker daemon이 필요하다. 빌드 결과는 `build/libs/`에 생성된다.

## 설정 원칙

- 기본 설정 파일은 `src/main/resources/application.yaml`이다.
- 환경별 주소와 인증정보는 환경변수 또는 배포 환경의 Secret으로 주입한다.
- Spring Boot BOM이 관리하는 종속성에는 개별 버전을 중복 지정하지 않는다.
- `.idea`, `.gradle`, `build`, `*.iml`과 로컬 시크릿은 커밋하지 않는다.
- API 내부에서 모듈 간 순환 의존성을 만들지 않는다.
- 결제 승인과 외부 Provider 호출은 백엔드에서만 수행한다.

## 다음 구성 작업

- Testcontainers MySQL·Redis 이미지 버전 고정
- 최초 Flyway Migration 추가
- 업무 모듈과 공개 Application Contract 구성
- ArchUnit 기반 모듈 의존성 검사 추가
- OpenAPI 3.1 문서 생성과 검증 구성
- SecurityFilterChain, Session Cookie, CSRF와 권한 정책 구현
- 로컬·테스트·운영 Profile과 환경변수 계약 정의
