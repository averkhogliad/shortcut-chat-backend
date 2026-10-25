# Соглашения разработки и безопасные изменения

## Стек

- Стек, версии, ID и test infrastructure — `openspec/specs/platform/stack/spec.md` и POM.
- Транзакционные границы — `openspec/specs/system/runtime-guarantees/spec.md`.
- Не добавляй JPA/Hibernate, MapStruct, Spring Security, OpenAPI, CI или линтеры без явной задачи.

## Существующие соглашения

- MVC-сервисы используют раскладку `api/{controller,data,handler}`, `config`, `core/{converter,model,persistence/{entity,repository},service}`, `outbox`. `notifications` и `mail-sender` устроены иначе; не выравнивай их без задачи.
- Сервисы, конвертеры и фабрики — интерфейс плюс `*Impl`.
- Сущности — `*Entity` с `@Table`. Репозитории наследуют минимальный `Repository<T, ID>` и объявляют только нужные методы.
- Результат сервиса — `EntityResult`; контроллер делает `.unwrap()`.
- Валидация — `require`/`requireNotNull`; Bean Validation нет.
- Конфигурация — `application.yaml` каждого модуля. Планировщики включаются вне профиля `test`.
- Логгер — делегат `by slf4j()` из `common`.

## Безопасные изменения

- Не перезаписывай файл с незакоммиченными изменениями, не прочитав его. Сохраняй существующие правки.
- Запрещены `git reset`, `git checkout --`, `git restore`, `git stash`, `git clean`, откат чужих правок и глобальный reformat.
- Не трогай несвязанные файлы и не исправляй технический долг в рамках локальной задачи.
- Не добавляй в репозиторий секреты, пароли, токены и реальные учётные данные. Конфигурацию контура задавай через properties/env.
- Не удаляй зависимости Liquibase/PostgreSQL и SQL-фикстуры в `notifications` без отдельной задачи.
