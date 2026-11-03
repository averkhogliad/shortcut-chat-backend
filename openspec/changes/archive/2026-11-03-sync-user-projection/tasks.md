# Tasks

## 1. `users` — события `UserUpdated` и `UserDeleted`

- [ ] 1.1 Создать `UserUpdatedMessageFactory` (type `UserUpdated`, version `v1`, body `User`) по образцу `UserCreatedMessageFactory`. Проверить: unit-тест `UserUpdatedMessageFactoryImplTest` создаёт `OutboxMessage` с type=`UserUpdated` и body=`User`.
- [ ] 1.2 Создать модель `UserDeleted { id: UUID }` в `users/core/model` и `UserDeletedMessageFactory` (type `UserDeleted`, body `UserDeleted`). Проверить: unit-тест проверяет type и payload содержит только id.
- [ ] 1.3 Добавить маршруты outbox `UserUpdated` и `UserDeleted` в канал `user-lifecycle` в `users/src/main/resources/application.yaml`. Проверить: приложение стартует без ошибки валидации `TxOutboxProperties` (`openspec validate`/запуск `UsersApp`).
- [ ] 1.4 В `UserService.update` вызвать `emit(userUpdatedMessageFactory.create(user))`, удалить TODO-логи `emitUserUpdatedLifecycleEvent`/`emitUserUpdatedStreamingEvent`. Проверить: `UserServiceTest` — кейс update захватывает `outboxEmitter.emit` с type=`UserUpdated` и сверяет body с ожидаемым `User`.
- [ ] 1.5 Добавить `UserService.delete(id): EntityResult<Unit>` (`@Transactional`): найти пользователя, удалить, emit `UserDeleted`; `EntityResult.NotFound` если отсутствует. Проверить: `UserServiceTest` — кейс удаления существующего захватывает `emit` с type=`UserDeleted` и body `UserDeleted(id)`; кейс несуществующего → `EntityResult.NotFound`.
- [ ] 1.6 Добавить `DELETE /users/{id}` в `UserController`, делегирующий `service.delete`. Проверить: integration-тест endpoint'а — 204/404; событие `UserDeleted` появляется в `UserLifecycle` (Kafka test utils), заголовки `X-Event-Name=UserDeleted` и стандартный набор.
- [ ] 1.7 Обновить существующие тесты `users` (генераторы `gen.kt`, `UserServiceTest`) под новые зависимости/фабрики. Проверить: `mvn -pl users test` проходит.

## 2. `chats` — consumer `UserLifecycle` и проекция `users`

- [ ] 2.1 Создать payload DTO `UserCreatedPayload`, `UserUpdatedPayload` (`id,name,email`), `UserDeletedPayload` (`id`) в `chats/core/model` или `chats/kafka`. Проверить: компилируется; поля соответствуют spec.
- [ ] 2.2 Создать `UserLifecycleTypeResolver` (`JsonTypeResolver`) по `X-Event-Name` → `UserCreatedPayload`/`UserUpdatedPayload`/`UserDeletedPayload` (иначе `Map<String,Any?>`), по образцу `notifications/.../ChatLifecycleTypeResolver.kt`. Проверить: unit-тест `UserLifecycleTypeResolverTest` для трёх имён и unknown.
- [ ] 2.3 В `chats/config/KafkaConfig` добавить bean `KAFKA_LISTENER_USER_LIFECYCLE` (`ConcurrentKafkaListenerContainerFactory`) с `JsonDeserializer` + resolver, type headers disabled. Проверить: контекст поднимается, bean присутствует (`ApplicationContextRunner` или spring-тест).
- [ ] 2.4 Добавить consumer group `chats-app` и `auto-offset-reset: earliest` в `chats/application.yaml` (`spring.kafka.consumer`). Проверить: запуск `ChatApp` читает конфиг без ошибок.
- [ ] 2.5 Создать `UserProjectionService` (`@Transactional` методы `applyCreated/applyUpdated/applyDeleted(id, eventTime, payload)`): загрузка по `id`, сравнение `eventTime <= lastSyncAt` → игнор; insert с `externalId=id.toString()`, `lastSyncAt=eventTime`; update полей `name/email` и `lastSyncAt`; `applyDeleted` → `deleted=true`, `lastSyncAt=eventTime`. Проверить: unit-тесты с mock `UserRepository` — сценарии insert/upsert/ignore-устаревшего/delete; `@LastModifiedDate` не перезаписывает ручное `lastSyncAt` (если перезаписывает — убрать аннотацию с поля в этой задаче).
- [ ] 2.6 Расширить `chats UserRepository` методом upsert/save (или `JdbcClient` conditional update) если требуется. Проверить: repository-тест на embedded/Postgres фикстурах — insert и update корректны, `external_id` уникален.
- [ ] 2.7 Создать `UserLifecycleEventsHandler` (`@KafkaListener(topics=["UserLifecycle"], containerFactory=KAFKA_LISTENER_USER_LIFECYCLE)`) с блокирующими `@KafkaHandler` для трёх payload и default handler (warn). Извлечение `X-Published-At` из заголовков, передача в `UserProjectionService`. Проверить: unit-тест handler'а с mock сервиса — три типа событий вызывают нужные методы; unknown → warning, без вызова сервиса.
- [ ] 2.8 Integration-тест: опубликовать в `UserLifecycle` записи `UserCreated`/`UserUpdated`/`UserDeleted` (Kafka test utils) и проверить строки в `chats.users` (`jdbc`) — вставка, обновление, `is_deleted=true`; дубли `X-Event-Id` не меняют состояние. Проверить: тест зелёный.
- [ ] 2.9 Проверить, что создание чата с user id из проекции проходит без FK violation (integration-тест `ChatEndpointTest` с предзаполненной проекцией через consumer). Проверить: тест зелёный.

## 3. `messages` — consumer `UserLifecycle` и проекция `users`

- [ ] 3.1 Создать payload DTO `UserCreatedPayload`, `UserUpdatedPayload`, `UserDeletedPayload` в `messages`. Проверить: компилируется.
- [ ] 3.2 Создать `UserLifecycleTypeResolver` в `messages`. Проверить: unit-тест `UserLifecycleTypeResolverTest`.
- [ ] 3.3 В `messages/config/KafkaConfig` добавить bean `KAFKA_LISTENER_USER_LIFECYCLE`. Проверить: контекст поднимается.
- [ ] 3.4 Добавить consumer group `messages-app` и `auto-offset-reset: earliest` в `messages/application.yaml`. Проверить: запуск `MessageApp` читает конфиг.
- [ ] 3.5 Создать `UserProjectionService` в `messages` (аналогично `chats`, D3/D4). Проверить: unit-тесты сценариев insert/upsert/ignore/delete.
- [ ] 3.6 Расширить `messages UserRepository` при необходимости. Проверить: repository-тест.
- [ ] 3.7 Создать `UserLifecycleEventsHandler` в `messages`. Проверить: unit-тест handler'а.
- [ ] 3.8 Integration-тест consumer'а: `UserLifecycle` → строки в `messages.users`; дубли идемпотентны. Проверить: тест зелёный.
- [ ] 3.9 Проверить, что `POST /messages` с author id из проекции проходит без FK violation (`MessageEndpointTest` с предзаполненной проекцией). Проверить: тест зелёный.

## 4. Интеграционная проверка и спецификации

- [ ] 4.1 Запустить полный build и тесты всех модулей (`mvn -pl users,chats,messages,notifications,mail-sender -am test`/it-профиль по `.agents/procedures/verification.md`). Проверить: сборка и тесты зелёные.
- [ ] 4.2 Проверить, что `openspec validate --change sync-user-projection --strict` проходит без ошибок. Проверить: команда зелёная.
- [ ] 4.3 Сверить, что спецификации `system/kafka-messaging`, `system/architecture`, `system/runtime-guarantees` delta согласованы с реализацией (payload, consumer'ы, идемпотентность). Проверить: ревью delta vs код; расхождений нет.

## Workflow follow-up

- Применить change через `/opsx-apply` после review.
- После реализации и прохождения проверок — archive change (`openspec archive`).