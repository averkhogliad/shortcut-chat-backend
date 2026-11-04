package io.averkhoglyad.shortcut.users.core.persistence.repository

import io.averkhoglyad.shortcut.common.test.executeSql
import io.averkhoglyad.shortcut.users.TestcontainersConfiguration
import io.averkhoglyad.shortcut.users.config.PersistenceConfig
import io.averkhoglyad.shortcut.users.test.gen.userEntities
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.maps.shouldContain
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.arbitrary.next
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import java.util.*

private const val INIT = "/repository/user/init.sql"
private const val CLEAR = "/repository/user/clear.sql"

@DataJdbcTest
@AutoConfigureTestDatabase(replace = NONE)
@Import(TestcontainersConfiguration::class, PersistenceConfig::class)
class UserRepositoryTest(
    private val target: UserRepository,
    private val jdbc: JdbcClient,
) : FreeSpec({

    beforeEach { executeSql(INIT) }
    afterEach { executeSql(CLEAR) }

    "save" - {
        "inserts new user and assigns id" {
            // given
            val entity = userEntities.next().apply {
                id = null
                email = "new-${UUID.randomUUID()}@example.com"
            }

            // when
            val result = target.save(entity)

            // then
            val id = result.id.shouldNotBeNull()
            jdbc.loadUserRow(id).shouldNotBeNull {
                this shouldContain ("name" to entity.name)
                this shouldContain ("email" to entity.email)
            }
        }

        "updates existing user" {
            // given
            val id = UUID.fromString("10000000-0000-0000-0000-000000000001")
            val entity = userEntities.next().apply {
                this.id = id
                email = "updated-${UUID.randomUUID()}@example.com"
            }

            // when
            target.save(entity)

            // then
            jdbc.loadUserRow(id).shouldNotBeNull {
                this shouldContain ("name" to entity.name)
                this shouldContain ("email" to entity.email)
            }
        }
    }

    "findById" - {
        "returns existing user" {
            // given
            val id = UUID.fromString("10000000-0000-0000-0000-000000000000")

            // when
            val result = target.findById(id)

            // then
            result.shouldNotBeNull()
            result.id shouldBe id
            result.name shouldBe "test-name-0"
            result.email shouldBe "test-0@e.mail"
        }

        "returns null for missing user" {
            // when
            val result = target.findById(UUID.fromString("99999999-0000-0000-0000-000000000000"))

            // then
            result.shouldBeNull()
        }
    }

    "findByEmail" - {
        "returns existing user" {
            // when
            val result = target.findByEmail("test-1@e.mail")

            // then
            result.shouldNotBeNull()
            result.id shouldBe UUID.fromString("10000000-0000-0000-0000-000000000001")
        }

        "returns null for missing email" {
            // when
            val result = target.findByEmail("missing@example.com")

            // then
            result.shouldBeNull()
        }
    }
})

private fun JdbcClient.loadUserRow(id: UUID): Map<String, Any?>? =
    this.sql("select * from users where id=:id")
        .param("id", id)
        .query()
        .listOfRows()
        .firstOrNull()
