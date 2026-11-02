# Границы модулей и владение данными

Обязательные инварианты.

- Границы модулей, compile-зависимости, владение БД, Kafka и синхронные вызовы — `openspec/specs/system/architecture/spec.md`.
- `messages` осознанно публикует событие после commit без outbox. Доставка не гарантирована. Не добавляй туда outbox и не меняй эту гарантию в локальной задаче.
- Гарантия доставки outbox определена в `openspec/specs/system/runtime-guarantees/spec.md`: at-least-once через claim/lease, retry с backoff до `DEAD`, восстановление просроченных lease, очистка по retention. Не расширяй её без отдельного решения.
- `tx-outbox` (ядро) и `tx-outbox-starter` (кастомизация и runtime) — не-сервисные библиотечные модули, БД не владеют. `users` и `chats` зависят от `common` и `tx-outbox-starter`. Ядро доменно-нейтрально (запись, claim/lease, публикация, очистка) и хранит данные через `JdbcClient`; маршрутизация, заголовки, `OutboxEmitter`, паблишер по умолчанию, планировщик, метрики и активация живут в стартере.
- Синхронизация read-моделей `users`/`chats` в другие сервисы — будущая задача. Не выдавай текущие несинхронизированные данные за источник истины.

Определения системы:

- `openspec/specs/system/architecture/spec.md` — модули, зависимости, владение данными, потоки.
- `openspec/specs/system/rest-api/spec.md` — HTTP-контракт `GET /members`.
- `openspec/specs/system/runtime-guarantees/spec.md` — outbox и direct publish.
