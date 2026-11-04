package io.averkhoglyad.shortcut.message.core.persistence.repository

import io.averkhoglyad.shortcut.common.test.asInstantColumn
import io.averkhoglyad.shortcut.common.test.betweenInclusive
import io.averkhoglyad.shortcut.common.test.executeSql
import io.averkhoglyad.shortcut.message.TestcontainersConfiguration
import io.averkhoglyad.shortcut.message.config.PersistenceConfig
import io.averkhoglyad.shortcut.message.core.persistence.repository.util.asMemberEntities
import io.averkhoglyad.shortcut.message.core.persistence.repository.util.chatEntities
import io.averkhoglyad.shortcut.message.core.persistence.repository.util.loadChatMemberRows
import io.averkhoglyad.shortcut.message.core.persistence.repository.util.loadChatRow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeSameSizeAs
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.date.shouldBeCloseTo
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.maps.shouldContain
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.set
import io.kotest.property.arbitrary.uuid
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.exhaustive
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import java.sql.Timestamp
import java.time.Instant
import java.util.*
import kotlin.time.Duration.Companion.milliseconds

private const val INIT = "/repository/chat/init.sql"
private const val CLEAR = "/repository/chat/clear.sql"

@DataJdbcTest
@AutoConfigureTestDatabase(replace = NONE)
@Import(TestcontainersConfiguration::class, PersistenceConfig::class)
class ChatRepositoryTest(
    val target: ChatRepository,
    val jdbc: JdbcClient,
) : FreeSpec({

    "save" - {
        beforeTest { executeSql(INIT) }
        afterTest { executeSql(CLEAR) }

        val members = Arb.set(existsUserIds.exhaustive(), 1..existsUserIds.size)
            .asMemberEntities()
        val ids = existsChatIds.exhaustive()

        "insert new Chat correctly" {
            checkAll(Arb.chatEntities(members = members)) { givenEntity ->
                // given
                val givenName = givenEntity.name

                // when
                val beforeSave = Instant.now()
                val result = target.save(givenEntity)
                val afterSave = Instant.now()

                // then
                val persistedId = result.id
                persistedId.shouldNotBeNull()
                result.lastSyncAt shouldBe betweenInclusive(beforeSave, afterSave)

                jdbc.loadChatRow(persistedId).shouldNotBeNull {
                    this shouldContain ("name" to givenName)
                    this["last_sync_at"]
                        .shouldNotBeNull()
                        .asInstantColumn()
                        .shouldBeCloseTo(result.lastSyncAt, 1.milliseconds)
                }

                jdbc.loadChatMemberRows(persistedId)
                    .shouldBeSameSizeAs(givenEntity.members)
                    .forEach { it["created_at"].shouldNotBeNull() }
            }
        }

        "update exists User correctly" {
            checkAll(Arb.chatEntities(ids = ids, members = members)) { givenEntity ->
                // given
                val givenId = givenEntity.id!!
                val givenName = givenEntity.name

                // when
                val result = target.save(givenEntity)

                // then
                result.id shouldNotBeNull { this shouldBeEqual givenId }

                jdbc.loadChatRow(givenId).shouldNotBeNull {
                    this shouldContain ("name" to givenName)
                    this["last_sync_at"]
                        .shouldNotBeNull()
                        .asInstantColumn()
                        .shouldBeCloseTo(result.lastSyncAt, 1.milliseconds)
                }

                jdbc.loadChatMemberRows(givenId)
                    .shouldBeSameSizeAs(givenEntity.members)
                    .forEach { it["created_at"].shouldNotBeNull() }
            }
        }
    }

    "findById" - {
        beforeTest { executeSql(INIT) }
        afterTest { executeSql(CLEAR) }

        "loads Chat correctly" {
            checkAll(existsChatIds.exhaustive()) { givenId ->
                val result = target.findById(givenId)
                result.shouldNotBeNull()

                val row = jdbc.loadChatRow(givenId)!!
                result.id shouldBe givenId
                result.name shouldBeEqual row["name"] as String
                result.lastSyncAt.shouldBeCloseTo((row["last_sync_at"] as Timestamp).toInstant(), 1.milliseconds)

                val memberRows = jdbc.loadChatMemberRows(givenId)
                result.members
                    .shouldHaveSize(memberRows.size)
                    .map { it.user.id } shouldContainExactly  memberRows.map { it["user_id"] }

                val memberRowsByUserId = memberRows.associateBy { it["user_id"] as UUID }
                result.members
                    .forEach {
                        it.createdAt.shouldNotBeNull()
                    }
            }
        }

        "returns null if Chat is not exists" {
            checkAll(Arb.uuid()) { givenId ->
                target.findById(givenId)
                    .shouldBeNull()
            }
        }
    }
})

private val existsUserIds = listOf(
    UUID.fromString("10000000-0000-0000-0000-000000000000"),
    UUID.fromString("10000000-0000-0000-0000-000000000001"),
    UUID.fromString("10000000-0000-0000-0000-000000000002"),
)

private val existsChatIds = listOf(
    UUID.fromString("10000001-0000-0000-0000-000000000000"),
    UUID.fromString("10000001-0000-0000-0000-000000000001"),
    UUID.fromString("10000001-0000-0000-0000-000000000002")
)
