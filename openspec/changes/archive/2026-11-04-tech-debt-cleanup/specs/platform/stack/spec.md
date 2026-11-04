# Spec Delta

## MODIFIED Requirements

### Requirement: Language and framework versions

The backend SHALL use Java 17, Kotlin 2.4.21, Spring Boot 4.1.2 (Jackson 3, `tools.jackson.*`; JSON annotations remain `com.fasterxml.jackson.annotation`), and Maven 3.9.16 through Maven Wrapper 3.3.4.

#### Scenario: Build toolchain

- **WHEN** the project is built
- **THEN** it SHALL use the Maven wrapper and the declared Java and Kotlin versions.

### Requirement: Supporting libraries

The backend SHALL use ShedLock 7.10.1, `java-uuid-generator` 5.2.0, Jakarta Mail API 2.1.5, and `loki-logback-appender` 2.0.3.

#### Scenario: Scheduled jobs

- **WHEN** a scheduled job needs a distributed lock
- **THEN** it SHALL use ShedLock.

### Requirement: Test stack

Tests SHALL use Kotest 6.2.6, MockK 1.14.11, JUnit Platform, and Testcontainers 2.0.5 where infrastructure is required.

#### Scenario: Unit test

- **WHEN** a test covers pure logic
- **THEN** it SHALL run without Docker.

### Requirement: Test infrastructure

Test modules SHALL use `common-test/SpringAwareKotestConfig`, which registers Kotest's `SpringExtension` (it also provides constructor autowiring), and a module-local `src/test/resources/kotest.properties`. Test modules SHALL use Testcontainers 2 (`testcontainers-postgresql`, `testcontainers-kafka`, `testcontainers-junit-jupiter`) and Spring Boot 4 test modules (`spring-boot-webmvc-test`, `spring-boot-data-jdbc-test`) for slice annotations. Test fixtures SHALL live under `src/test/resources/it/**` or `src/test/resources/repository/**`. All tests SHALL run in the `test` source set; no separate integration-test profile is configured.

#### Scenario: Repository test

- **WHEN** a test needs SQL fixtures
- **THEN** the fixtures SHALL live under `src/test/resources/repository/**`.
