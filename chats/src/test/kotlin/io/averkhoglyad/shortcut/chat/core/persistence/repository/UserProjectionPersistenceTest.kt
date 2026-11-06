package io.averkhoglyad.shortcut.chat.core.persistence.repository

import io.averkhoglyad.shortcut.chat.TestcontainersConfiguration
import io.averkhoglyad.shortcut.chat.config.PersistenceConfig
import io.averkhoglyad.shortcut.common.test.asInstantColumn
import io.averkhoglyad.shortcut.common.test.executeSql
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import java.time.Instant
import java.util.*

private const val INIT = "/repository/user/init.sql"
private const val CLEAR = "/repository/user/clear.sql"

@DataJdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration::class, PersistenceConfig::class)
class UserProjectionPersistenceTest(
    val target: UserRepository,
    val jdbc: JdbcClient,
) : FreeSpec({

    beforeTest { executeSql(INIT) }
    afterTest { executeSql(CLEAR) }

    "manually set lastSyncAt survives save" {
        // given
        val id = UUID.fromString("10000000-0000-0000-0000-000000000001")
        val eventTime = Instant.parse("2025-06-01T12:00:00Z")
        val existing = target.findById(id).shouldNotBeNull()
        existing.lastSyncAt = eventTime

        // when
        val result = target.save(existing)

        // then
        result.lastSyncAt shouldBe eventTime
        jdbc.loadUserRow(id).shouldNotBeNull {
            this["last_sync_at"]
                .shouldNotBeNull()
                .asInstantColumn() shouldBe eventTime
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
