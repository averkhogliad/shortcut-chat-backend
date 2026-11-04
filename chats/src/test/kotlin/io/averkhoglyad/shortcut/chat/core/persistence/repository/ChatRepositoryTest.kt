package io.averkhoglyad.shortcut.chat.core.persistence.repository

import io.averkhoglyad.shortcut.chat.TestcontainersConfiguration
import io.averkhoglyad.shortcut.chat.config.PersistenceConfig
import io.averkhoglyad.shortcut.common.test.betweenInclusive
import io.averkhoglyad.shortcut.common.test.executeSql
import io.averkhoglyad.shortcut.common.test.shouldBeEqual
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeSameSizeAs
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.maps.shouldContain
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.set
import io.kotest.property.arbitrary.uuid
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.andNull
import io.kotest.property.exhaustive.exhaustive
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import java.time.Instant
import java.util.*

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
        val owners = existsUserIds.exhaustive().andNull()
        val ids = existsChatIds.exhaustive()

        "insert new Chat correctly" {
            checkAll(Arb.chatEntities(ownerIds = owners, members = members)) { givenEntity ->
                // given
                val givenName = givenEntity.name
                val givenOwnerId = givenEntity.owner?.id

                // when
                val beforeSave = Instant.now()
                val result = target.save(givenEntity)
                val afterSave = Instant.now()

                // then
                val persistedId = result.id
                persistedId.shouldNotBeNull()
                result.createdAt shouldBe betweenInclusive(beforeSave, afterSave)

                jdbc.loadChatRow(persistedId).shouldNotBeNull {
                    this shouldContain ("name" to givenName)
                    this shouldContain ("owner_id" to givenOwnerId)
                    this["created_at"].shouldNotBeNull()
                }

                jdbc.loadChatMemberRows(persistedId)
                    .shouldBeSameSizeAs(givenEntity.members)
                    .forEach { it["created_at"].shouldNotBeNull() }
            }
        }

        "update exists User correctly" {
            checkAll(Arb.chatEntities(ids = ids, ownerIds = owners, members = members)) { givenEntity ->
                // given
                val givenId = givenEntity.id!!
                val givenName = givenEntity.name
                val givenOwnerId = givenEntity.owner?.id

                val originalRow = jdbc.loadChatRow(givenId)!!

                // when
                val result = target.save(givenEntity)

                // then
                val persistedId = result.id
                persistedId.shouldNotBeNull()

                jdbc.loadChatRow(persistedId).shouldNotBeNull {
                    this shouldContain ("name" to givenName)
                    this shouldContain ("owner_id" to givenOwnerId)
                    this["created_at"]!! shouldBeEqual originalRow["created_at"]!!
                }

                jdbc.loadChatMemberRows(persistedId)
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
                result.owner?.id?.shouldBeEqual(row["owner_id"])
                result.createdAt.shouldNotBeNull()

                val memberRows = jdbc.loadChatMemberRows(givenId)
                result.members shouldHaveSize memberRows.size
                result.members.forEach { member -> member.chat.id shouldBeEqual givenId }

                result.members.map { it.user.id } shouldContainExactly  memberRows.map { it["user_id"] }

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

    "findMembersByChatIdIsIn" - {
        beforeTest { executeSql(INIT) }
        afterTest { executeSql(CLEAR) }

        "loads Chat Members correctly" {
            checkAll(existsChatIds.exhaustive()) { givenId ->
                val result = target.findMembersByChatIdIsIn(listOf(givenId))

                val rows = jdbc.loadChatMemberRows(givenId)

                result shouldHaveSize rows.size
                result.forEach { member -> member.chat.id shouldBeEqual givenId }

                result.map { it.user.id } shouldContainExactly  rows.map { it["user_id"] }

                val memberRowsByUserId = rows.associateBy { it["user_id"] as UUID }

                result.forEach {
                    it.createdAt.shouldNotBeNull()
                }
            }
        }
    }
})

private fun JdbcClient.loadChatRow(id: UUID): Map<String, Any?>? {
    return this.sql("select * from chats where id=:id")
        .param("id", id)
        .query()
        .listOfRows()
        .firstOrNull()
}

private fun JdbcClient.loadChatMemberRows(chatId: UUID): List<Map<String, Any?>> {
    return this.sql("select * from chat_members where chat_id=:chatId")
        .param("chatId", chatId)
        .query()
        .listOfRows()
}

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
