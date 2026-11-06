package io.averkhoglyad.shortcut.chat.core.persistence.repository

import io.averkhoglyad.shortcut.chat.TestcontainersConfiguration
import io.averkhoglyad.shortcut.chat.config.PersistenceConfig
import io.averkhoglyad.shortcut.chat.core.persistence.entity.UserEntity
import io.averkhoglyad.shortcut.common.test.asInstantColumn
import io.averkhoglyad.shortcut.common.test.executeSql
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.date.shouldBeCloseTo
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.maps.shouldContain
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.uuid
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.exhaustive
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import java.sql.Timestamp
import java.util.*
import kotlin.time.Duration.Companion.milliseconds

private const val INIT = "/repository/user/init.sql"
private const val CLEAR = "/repository/user/clear.sql"

@DataJdbcTest
@AutoConfigureTestDatabase(replace = NONE)
@Import(TestcontainersConfiguration::class, PersistenceConfig::class)
class UserRepositoryTest(
    val target: UserRepository,
    val jdbc: JdbcClient,
) : FreeSpec({
    "save" - {
        beforeTest { executeSql(INIT) }
        afterTest { executeSql(CLEAR) }

        "insert new User correctly" {
            checkAll(Arb.userEntities()) { givenEntity ->
                // given
                givenEntity.externalId = UUID.randomUUID().toString() // force unique value
                val givenExternalId = givenEntity.externalId
                val givenName = givenEntity.name
                val givenEmail = givenEntity.email
                val givenIsDeleted = givenEntity.deleted
                val givenLastSyncAt = givenEntity.lastSyncAt

                // when
                val result = target.save(givenEntity)

                // then
                val persistedId = result.id
                persistedId.shouldNotBeNull()
                result.lastSyncAt shouldBe givenLastSyncAt

                jdbc.loadUserRow(persistedId).shouldNotBeNull {
                    this shouldContain ("external_id" to givenExternalId)
                    this shouldContain ("name" to givenName)
                    this shouldContain ("email" to givenEmail)
                    this shouldContain ("is_deleted" to givenIsDeleted)
                    this["last_sync_at"]
                        .shouldNotBeNull()
                        .asInstantColumn()
                        .shouldBeCloseTo(result.lastSyncAt, 1.milliseconds)
                }
            }
        }

        "update exists User correctly" {
            checkAll(Arb.userEntities(ids = existsUserIds.exhaustive())) { givenEntity ->
                // given
                val givenId = givenEntity.id!!
                val givenName = givenEntity.name
                val givenEmail = givenEntity.email
                val givenIsDeleted = givenEntity.deleted
                val givenLastSyncAt = givenEntity.lastSyncAt

                // when
                val result = target.save(givenEntity)

                // then
                result.id shouldNotBeNull { this shouldBeEqual givenId }
                result.lastSyncAt shouldBe givenLastSyncAt

                jdbc.loadUserRow(givenId).shouldNotBeNull {
                    this shouldContain ("name" to givenName)
                    this shouldContain ("email" to givenEmail)
                    this shouldContain ("is_deleted" to givenIsDeleted)
                    this["last_sync_at"]
                        .shouldNotBeNull()
                        .asInstantColumn()
                        .shouldBeCloseTo(result.lastSyncAt, 1.milliseconds)
                }
            }
        }

        "update doesn't change externalId" {
            checkAll(existsUserIds.exhaustive()) { givenId ->
                // given
                val originalRow = jdbc.loadUserRow(givenId)!!
                val originalExternalId = originalRow["external_id"] as String
                val givenEntity = UserEntity().apply(originalRow)

                // when
                target.save(givenEntity)

                // then
                jdbc.loadUserRow(givenId)
                    .shouldNotBeNull {
                        this shouldContain ("external_id" to originalExternalId)
                    }
            }
        }
    }

    "findById" - {
        beforeTest { executeSql(INIT) }
        afterTest { executeSql(CLEAR) }

        "loads User correctly" {
            checkAll(existsUserIds.exhaustive()) { givenId ->
                val result = target.findById(givenId)
                result.shouldNotBeNull()

                val row = jdbc.loadUserRow(givenId)!!
                result.id shouldBe givenId
                result.externalId shouldBeEqual row["external_id"] as String
                result.name shouldBeEqual row["name"] as String
                result.email shouldBeEqual row["email"] as String
                result.deleted shouldBeEqual row["is_deleted"] as Boolean
                result.lastSyncAt.shouldBeCloseTo((row["last_sync_at"] as Timestamp).toInstant(), 1.milliseconds)
            }
        }

        "returns null if User is not exists" {
            checkAll(Arb.uuid()) { givenId ->
                target.findById(givenId)
                    .shouldBeNull()
            }
        }
    }

    "findByIdIsInAndDeletedFalse" - {
        beforeTest { executeSql(INIT) }
        afterTest { executeSql(CLEAR) }

        "returns only non-deleted Users for a mixed id set" {
            // given
            val givenIds = existsUserIds

            // when
            val result = target.findByIdIsInAndDeletedFalse(givenIds)

            // then
            result.mapNotNull { it.id }.toSet() shouldBe setOf(activeUserId)
            result.forEach { it.deleted shouldBe false }
        }

        "returns the User when only active ids are requested" {
            // given
            val givenIds = listOf(activeUserId)

            // when
            val result = target.findByIdIsInAndDeletedFalse(givenIds)

            // then
            result.mapNotNull { it.id }.toSet() shouldBe setOf(activeUserId)
        }

        "returns empty collection if all requested Users are deleted" {
            // given
            val givenIds = listOf(deletedUserId)

            // when
            val result = target.findByIdIsInAndDeletedFalse(givenIds)

            // then
            result shouldBe emptyList()
        }

        "returns empty collection if Users are not projected" {
            checkAll(Arb.uuid()) { givenId ->
                target.findByIdIsInAndDeletedFalse(listOf(givenId)) shouldBe emptyList()
            }
        }
    }
})

private fun JdbcClient.loadUserRow(id: UUID): Map<String, Any?>? {
    return this.sql("select * from users where id=:id")
        .param("id", id)
        .query()
        .listOfRows()
        .firstOrNull()
}

private val deletedUserId = UUID.fromString("10000000-0000-0000-0000-000000000000")
private val activeUserId = UUID.fromString("10000000-0000-0000-0000-000000000001")

private val existsUserIds = listOf(deletedUserId, activeUserId)

private fun UserEntity.apply(row: Map<String, Any?>): UserEntity {
    id = row["id"] as UUID
    name = row["name"] as String
    email = row["email"] as String
    deleted = row["is_deleted"] as Boolean
    externalId = row["external_id"] as String
    lastSyncAt = (row["last_sync_at"] as java.sql.Timestamp).toInstant()
    return this
}
