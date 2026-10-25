# Проверка изменений

Объём проверки выбирай соразмерно риску. Полный `clean verify` для каждой мелкой правки не требуется.

## Выбор проверки

- Сначала определи радиус: затронутый модуль, `common`, POM, миграции, REST-контракт, Kafka-контракт.
- Чистая логика — unit-тест на Kotest и MockK, без Docker.
- SQL, маппинг или миграция — `@DataJdbcTest` с Testcontainers; фикстуры — по `openspec/specs/platform/stack/spec.md`.
- Контроллер, JSON или ошибки — `@SpringBootTest`, `@AutoConfigureMockMvc`, `@ActiveProfiles("test")`, `@Import(TestcontainersConfiguration::class)`; фикстуры — по `openspec/specs/platform/stack/spec.md`.
- Публикация Kafka — проверяй событие и заголовки по `openspec/specs/system/kafka-messaging/spec.md`; тест-образец — `messages/src/test/kotlin/io/averkhoglyad/shortcut/message/it/MessageEndpointTest.kt`.
- Минимум для локальной правки — компиляция и тесты затронутого модуля.
- Изменения в `common`, `common-test` или POM требуют прогона зависимых модулей; для широкого изменения допустим `clean verify`.
- Изменения только в документации или правилах не требуют сборки и тестов приложения.

## Команды

- Модуль: `.\mvnw.cmd -pl <module> -am test`
- Все тесты: `.\mvnw.cmd test`
- Полная сборка: `.\mvnw.cmd clean verify`
- Unix: `./mvnw ...`
- Команды запускай только через wrapper.

## Известные ограничения

- Testcontainers-тесты требуют Docker. Если Docker недоступен, зафиксируй это как ограничение окружения, а не как успешную проверку.
- Инфраструктурные ограничения тестов — `openspec/specs/platform/stack/spec.md`.
- Ограничение `common` с компиляцией тестов: POM не объявляет `test-compile`; `FlowTest` не компилируется и рабочим покрытием не является.
- Линтера, форматтера и отдельного typecheck в проекте нет. Не выдумывай команды и не делай массовый reformat.
- Часть модулей использует Postgres-контейнер, `messages` дополнительно Kafka-контейнер. Учитывай стоимость и нестабильность таких тестов.
