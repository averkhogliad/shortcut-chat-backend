# Соглашения разработки и безопасные изменения

## Стек

- Java 17, Kotlin 1.9.25, Spring Boot 3.4.0, Maven Wrapper 3.9.9.
- Spring Data JDBC, PostgreSQL, Liquibase XML, Kafka.
- Тесты: Kotest, MockK/SpringMockK, Testcontainers.
- `mail-sender` использует Redis для идемпотентности.
- Не добавляй JPA/Hibernate, MapStruct, Spring Security, OpenAPI, CI или линтеры без явной задачи.

## Существующие соглашения

- MVC-сервисы используют раскладку `api/{controller,data,handler}`, `config`, `core/{converter,model,persistence/{entity,repository},service}`, `outbox`. `notifications` и `mail-sender` устроены иначе; не выравнивай их без задачи.
- Сервисы, конвертеры и фабрики — интерфейс плюс `*Impl`.
- Сущности — `*Entity` с `@Table`. Репозитории наследуют минимальный `Repository<T, ID>` и объявляют только нужные методы.
- ID — UUID v7 через общий `IdGenerationBeforeConvertCallback`.
- Результат сервиса — `EntityResult`; контроллер делает `.unwrap()`.
- Валидация — `require`/`requireNotNull`; Bean Validation нет.
- Транзакции — `@Transactional` на сервисах; post-commit действия — через `transaction { afterCommit { ... } }`.
- Конфигурация — `application.yaml` каждого модуля. Планировщики включаются вне профиля `test`.
- Логгер — делегат `by slf4j()` из `common`.

## Безопасные изменения

- Перед правкой проверь `git status` и diff целевых файлов.
- Не перезаписывай файл с незакоммиченными изменениями, не прочитав его. Сохраняй существующие правки.
- Запрещены `git reset`, `git checkout --`, `git restore`, `git stash`, `git clean`, откат чужих правок и глобальный reformat.
- Не трогай несвязанные файлы и не исправляй технический долг в рамках локальной задачи.
- Не добавляй в репозиторий секреты, пароли, токены и реальные учётные данные. Конфигурацию контура задавай через properties/env.
- Коммит — только по явной просьбе.
- В отчёте перечисляй изменённые файлы и выполненные проверки.
