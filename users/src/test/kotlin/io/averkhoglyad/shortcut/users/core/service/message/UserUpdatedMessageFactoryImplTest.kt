package io.averkhoglyad.shortcut.users.core.service.message

import io.averkhoglyad.shortcut.users.test.gen.users
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.checkAll

class UserUpdatedMessageFactoryImplTest: FreeSpec({

    val factory = UserUpdatedMessageFactoryImpl()

    "create returns Message with correct values" {
        checkAll(users) { user ->
            val result = factory.create(user)
            result.type shouldBe "UserUpdated"
            result.version shouldBe "v1"
            result.key shouldBe user.id.toString()
            result.body shouldBe user
        }
    }
})
