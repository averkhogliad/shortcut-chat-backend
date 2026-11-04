package io.averkhoglyad.shortcut.users.core.service.message

import io.averkhoglyad.shortcut.users.core.model.UserDeleted
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.uuid
import io.kotest.property.checkAll

class UserDeletedMessageFactoryImplTest: FreeSpec({

    val factory = UserDeletedMessageFactoryImpl()

    "create returns Message with correct values" {
        checkAll(Arb.uuid()) { id ->
            val body = UserDeleted(id)
            val result = factory.create(body)
            result.type shouldBe "UserDeleted"
            result.version shouldBe "v1"
            result.key shouldBe id.toString()
            result.body shouldBe body
        }
    }
})
