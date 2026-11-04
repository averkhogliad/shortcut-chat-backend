package io.averkhoglyad.shortcut.users.core.service.message

import io.averkhoglyad.shortcut.users.test.gen.users
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import io.kotest.property.checkAll

class SendCreatedUserNotificationMessageFactoryImplTest : FreeSpec({

    val factory = SendCreatedUserNotificationMessageFactoryImpl()

    "create returns Message with correct values" {
        checkAll(users) { user ->
            val result = factory.create(user)
            result.type shouldBeEqual "SendCreatedUserNotification"
            result.version shouldBeEqual "v1"
            result.key shouldBe null
            result.body should {
                it.to shouldContainExactly listOf(user.email)
                it.subject shouldBeEqual  "Welcome ${user.name}!"
                it.subject shouldContain user.name
                it.body shouldStartWith "Hello ${user.name}."
                it.body shouldContain  "Welcome"
            }
        }
    }
})