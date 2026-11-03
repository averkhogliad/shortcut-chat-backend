# Design

## Context

См. `proposal.md` — Why. Текущее состояние: `users` публикует `UserCreated` в `UserLifecycle` через outbox (`users/application.yaml:48-51`), consumer'ы нет. `chats` и `messages` владеют локальными таблицами `users` с `external_id NOT NULL UNIQUE`, `last_sync_at`, `is_deleted` (`chats/.../001__Create-User-table.xml`, `messages/.../001__Create-User-table.xml`) и FK из `chat_members`, `chats.owner_id`, `messages.author_id`. `users` сервис не имеет понятия `external_id` (`UserEntity` содержит только `id,name,email`). `UserService.update` (`UserService.kt:92-114`) события не шлёт. Удаление пользователя не реализовано.

Сервисы `chats` и `messages` — Spring MVC (blocking), в отличие от `notifications` (WebFlux). Consumer должен быть блокирующим. Spring-kafka уже в стеке.

## Goals / Non-Goals

**Goals:**
- `UserUpdated` и `UserDeleted` публикуются из `users` через outbox в одной транзакции с агрегатом.
- `chats` и `messages` потребляют `UserLifecycle` и поддерживают локальную проекцию `users` идемпотентно и упорядоченно по времени события.
- `DELETE /users/{id}` инициирует `UserDeleted`.
- `PUT /users/{id}` инициирует `UserUpdated`.

**Non-Goals:**
- Синхронизация read-модели `chats` в `messages` (отдельная задача — `ChatCreated` consumer).
- Мягкое удаление через `UserUpdated` и `is_deleted` в payload `UserUpdated`.
- Вынос общего consumer-инфраструктуры и payload DTO в `common` — дублирование сохраняется по аналогии с `ChatLifecycleTypeResolver`.
- Retry/DLQ для consumer'ов проекции — не реализуется (вне scope, как в существующих consumer'ах).
- Backfill через snapshot-endpoint (`GET /users`); backfill только за счёт `auto-offset-reset=earliest` для новых групп.

## Decisions

### D1. Payload `UserUpdated` = `User { id, name, email }`, `UserDeleted` = `UserDeleted { id }`
Соответствует спецификации (`specs/system/kafka-messaging`). `UserUpdated` не содержит `external_id` и `is_deleted`: пользователи-сервис их не имеет, а мягкое удаление смоделировано отдельным событием. Альтернатива — добавить `externalId` в payload — отвергнута: источник не владеет этим полем.

### D2. Заполнение `external_id` в проекции: `external_id = id.toString()`
Локальные таблицы `chats`/`messages` требуют `external_id NOT NULL UNIQUE`, но событие его не несёт. На insert в проекцию кладём `external_id = id.toString()` (поле `@InsertOnlyProperty`, повторно не меняется). Альтернатива — миграция с drop колонки — отвергнута как более инвазивная; поле оставлено для совместимости с существующими фикстурами.

### D3. Упорядоченность по `X-Published-At`, `last_sync_at` хранит время события
Время события = заголовок `X-Published-At` (Instant). Consumer сравнивает его со значением `users.last_sync_at` для данного `id`: событие с `eventTime <= last_sync_at` игнорируется. На успешное применение `last_sync_at` обновляется до `eventTime`.
Реализация: загрузка строки по `id` через `UserRepository.findById`, сравнение в сервисе, затем `save`. Альтернатива — условный `UPDATE ... WHERE last_sync_at < :eventTime` через `JdbcClient` — возможна на этапе реализации; выбор за исполнителем, наблюдаемое поведение то же.
Риск: `@LastModifiedDate` на `lastSyncAt` может перезаписать явно заданное значение аудитом Spring Data JDBC перед `save`. На этапе реализации убедиться, что ручная установка `lastSyncAt` сохраняется (при необходимости убрать `@LastModifiedDate` с этого поля или отключить аудит для проекции).

### D4. `UserDeleted` → `is_deleted = true`, hard delete не делается
Удаление строки проекции нарушило бы FK для уже существующих `chat_members`/`chats.owner_id`/`messages.author_id`. Поэтому `UserDeleted` выставляет `is_deleted = true` и обновляет `last_sync_at`; строка остаётся. Новые агрегаты не должны ссылаться на удалённого пользователя — это ответственность сервисов при создании (на уровне REST не валидируется в scope).

### D5. Consumer'ы блокирующие, Spring MVC
`chats` и `messages` — Spring MVC. `@KafkaListener` с блокирующими `@KafkaHandler` (без `suspend`). Контейнер-фабрика `KAFKA_LISTENER_USER_LIFECYCLE` в `KafkaConfig` каждого сервиса, по образцу `notifications/config/KafkaConfig.kt`, но без WebFlux. Десериализация: `JsonDeserializer` + `JsonTypeResolver` по `X-Event-Name` (тип-заголовки отключены), аналогично `ChatLifecycleTypeResolver`. Каждый сервис определяет свои payload DTO (`UserCreated`, `UserUpdated`, `UserDeleted`) и свой resolver — дублирование осознанное.

### D6. Consumer group и offset
`group-id`: `chats-app`, `messages-app` (в `application.yaml`). `auto-offset-reset=earliest` для новых групп — backfill уже опубликованных `UserCreated` из retention-окна. Риск: события старше retention потеряны; ручной backfill вне scope.

### D7. `users` — новые фабрики и маршруты outbox
- `UserUpdatedMessageFactory` (type `UserUpdated`, body `User`).
- `UserDeletedMessageFactory` (type `UserDeleted`, body `UserDeleted { id }`).
- `application.yaml`: маршруты `UserUpdated` и `UserDeleted` в существующий канал `user-lifecycle` → топик `UserLifecycle`.
- `UserService.update` — вызов `emit(userUpdatedMessageFactory.create(user))`, TODO-логи удаляются.
- `UserService.delete(id)` — `repository.deleteById` (или поиск + удаление), `EntityResult`; emit `UserDeleted`. `DELETE /users/{id}` в `UserController`.

### D8. Транзакционность consumer'а
`@Transactional` на методе `UserProjectionService.apply*`. Десериализация и диспетческая логика в handler'е вне транзакции; применение проекции — в транзакции.

## Risks / Trade-offs

- [Outbox at-least-once → дубли] → идемпотентность по `id` + `eventTime` (D3). Дубли `UserCreated` для отсутствующей строки: вставка по `id` (PK) защитит; повторный insert даст нарушение PK — обрабатывать как уже применённое.
- [Нарушение порядка `Created`/`Updated`/`Deleted`] → сравнение `eventTime` с `last_sync_at` (D3).
- [`@LastModifiedDate` перезаписывает `last_sync_at`] → проверить на реализации (D3).
- [Events старше retention не backfill'ятся] → accepted; ручной backfill вне scope (D6).
- [Дублирование payload DTO/resolver в двух сервисах] → accepted (Non-Goal); вынос в `common` — отдельная задача.
- [`UserDeleted` оставляет строку] → FK сохранён, но `is_deleted=true` не валидируется при создании новых агрегатов в scope (D4).
- [Consumer'ы `chats`/`messages` и REST в одном приложении — blocking listener контейнер] → Spring-kafka `ConcurrentKafkaListenerContainerFactory` работает в MVC-приложении; отдельный поток пула listener'ов.

## Migration Plan

1. Deploy `users` (новые события + `DELETE` endpoint) — старые `UserCreated` уже в топике, новые `UserUpdated`/`UserDeleted` начинают публиковаться.
2. Deploy `chats` и `messages` с consumer'ами (`auto-offset-reset=earliest`) — backfill из retention-окна.
3. Rollback: отключить consumer'ы (удалить `@KafkaListener` или `enabled` флаг) — проекции перестают актуализироваться, но существующие данные не повреждаются. `users` rollback — `UserUpdated`/`UserDeleted` перестают публиковаться, `DELETE` endpoint отключается.
4. Миграций БД не требуется.

## Open Questions

- Нужно ли валидировать `is_deleted` при создании чата/сообщения (запрет ссылаться на удалённого пользователя)? Поведение не spec'ифицировано в этом change; отложить.
- Стоит ли вынести payload DTO и `TypeResolver` в `common` сейчас? Non-Goal; revisit после второго consumer'а.