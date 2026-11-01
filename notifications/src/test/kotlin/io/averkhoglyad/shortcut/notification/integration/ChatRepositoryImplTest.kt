package io.averkhoglyad.shortcut.notification.integration

import io.averkhoglyad.shortcut.notification.data.ChatMembers
import io.averkhoglyad.shortcut.notification.data.UserRef
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import java.util.UUID.randomUUID

class ChatRepositoryImplTest : FreeSpec({

    val endpoint = mockk<ChatMembersEndpoint>()
    val target = ChatRepositoryImpl(endpoint)

    afterTest {
        clearAllMocks()
    }

    "members" - {
        "returns members grouped by chat id" {
            // given
            val firstChatId = randomUUID()
            val secondChatId = randomUUID()
            val firstMembers = listOf(UserRef(randomUUID()), UserRef(randomUUID()))
            val secondMembers = listOf(UserRef(randomUUID()))
            every { endpoint.members(listOf(firstChatId, secondChatId)) } returns flowOf(
                listOf(
                    ChatMembers(firstChatId, firstMembers),
                    ChatMembers(secondChatId, secondMembers),
                )
            )

            // when
            val result = target.members(listOf(firstChatId, secondChatId)).first()

            // then
            result shouldContainExactly mapOf(
                firstChatId to firstMembers,
                secondChatId to secondMembers,
            )
        }

        "returns empty map for empty chat id collection" {
            // given
            every { endpoint.members(emptyList()) } returns flowOf(emptyList())

            // when
            val result = target.members(emptyList()).first()

            // then
            result shouldBe emptyMap()
        }
    }
})
