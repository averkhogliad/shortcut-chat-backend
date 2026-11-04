package io.averkhoglyad.shortcut.message.core.persistence.repository

import io.averkhoglyad.shortcut.common.test.executeSql
import io.averkhoglyad.shortcut.message.TestcontainersConfiguration
import io.averkhoglyad.shortcut.message.config.PersistenceConfig
import io.averkhoglyad.shortcut.message.core.persistence.repository.util.countChatMessageRows
import io.averkhoglyad.shortcut.message.core.persistence.repository.util.loadMessageRow
import io.averkhoglyad.shortcut.message.core.persistence.repository.util.messageEntities
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.maps.shouldContain
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.exhaustive
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import java.time.Instant
import java.util.*

private const val INIT = "/repository/message/init.sql"
private const val CLEAR = "/repository/message/clear.sql"

@DataJdbcTest
@AutoConfigureTestDatabase(replace = NONE)
@Import(TestcontainersConfiguration::class, PersistenceConfig::class)
class MessageRepositoryTest(
    val target: MessageRepository,
    val jdbc: JdbcClient,
) : FreeSpec({

    "save" - {
        beforeTest { executeSql(INIT) }
        afterTest { executeSql(CLEAR) }

        "insert new Message correctly" {
            val messageEntities = Arb
                .messageEntities(authorIds = existsUserIds.exhaustive(), chatIds = existsChatIds.exhaustive())
            checkAll(messageEntities) { givenEntity ->
                // given
                val givenText = givenEntity.text
                val givenChatId = givenEntity.chat.id!!
                val givenAuthorId = givenEntity.author.id!!
                val originalTotalChatMessages = jdbc.countChatMessageRows(givenChatId)

                // when
                val beforeSave = Instant.now()
                val result = target.save(givenEntity)
                val afterSave = Instant.now()

                // then
                val persistedId = result.id
                persistedId.shouldNotBeNull()
                result.createdAt.shouldNotBeNull()

                jdbc.countChatMessageRows(givenChatId) shouldBe (originalTotalChatMessages + 1)

                jdbc.loadMessageRow(persistedId).shouldNotBeNull {
                    this shouldContain ("text" to givenText)
                    this shouldContain ("chat_id" to givenChatId)
                    this shouldContain ("author_id" to givenAuthorId)
                    this["created_at"].shouldNotBeNull()
                }
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
