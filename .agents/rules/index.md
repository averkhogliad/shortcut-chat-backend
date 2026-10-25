# Правила backend

Файлы в этом каталоге — обязательные нормы. Индекс только направляет к нужному файлу и не заменяет его содержимое.

Применяй файл, если задача затрагивает его область:

- `architecture/module-boundaries.md` — модули, compile-зависимости, владение БД, межсервисное взаимодействие, Kafka, outbox.
- `architecture/contracts.md` — REST API, DTO, ответы и ошибки, Kafka-топики, payload, заголовки, consumer'ы, сериализация.
- `development/conventions.md` — изменения кода, конфигурации, POM, миграций; работа с незакоммиченными изменениями.
- `testing/test-style.md` — стиль `*Test.kt` и генераторов данных: FreeSpec, Arb/Exhaustive, моки.

## Specs

Определения системы — в `openspec/specs/`.

Specs — источник истины для ожидаемого поведения. Rules ссылаются на них и не пересказывают.

## Другие документы

- Domain Profile — `.agents/context/backend.md`.
- Процедура проверки — `.agents/procedures/verification.md` (Project Rules).
