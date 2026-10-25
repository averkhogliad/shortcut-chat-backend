package io.averkhoglyad.shortcut.notification.service

import io.averkhoglyad.shortcut.notification.data.*
import io.averkhoglyad.shortcut.notification.integration.ChatRepository
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeZero
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import java.time.Duration
import java.util.UUID.randomUUID
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.toKotlinDuration

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationServiceImplTest : FreeSpec({

    afterTest {
        clearAllMocks()
    }

    "NotificationService with 500ms time window" - {
        val usersRepo = mockk<ChatRepository>()
        val windowSize = Duration.ofMillis(500)
        val target = NotificationServiceImpl(usersRepo, windowSize)

        "can handle a single event" {
            runTest {
                // given
                val givenChat = ChatRef(id = randomUUID())
                val givenEvent = DebugEvent(chat = givenChat)
                val givenMembers = listOf(UserRef(id = randomUUID()))

                every { usersRepo.members(listOf(givenChat.id)) } returns flowOf(mapOf(givenChat.id to givenMembers))

                // when
                val eventsDef = async { target.events().first() }
                yield() // shift coroutine to execute async block and subscribe for events firstly
                launch { // emit event in other coroutine
                    target.handleEvent(givenEvent)
                }
                val events = eventsDef.await() // waiting for result

                // then
                events shouldHaveSize 1
                events.first() shouldBe givenEvent + givenMembers 
            }
        }

        "can handle multiple events within the time window" {
            runTest {
                // given
                val givenChat = ChatRef(id = randomUUID())
                val givenEvents = listOf(DebugEvent(chat = givenChat), DebugEvent(chat = givenChat))
                val givenMembers = listOf(UserRef(id = randomUUID()))

                every { usersRepo.members(listOf(givenChat.id)) } returns flowOf(mapOf(givenChat.id to givenMembers))

                // when
                launch {
                    givenEvents.forEach { event ->
                        delay(100)
                        target.handleEvent(event)
                    }
                }
                val events = target.events().first()

                // then
                events shouldBe givenEvents.map{ it + givenMembers }
            }
        }

        "can handle multiple events outside the time window" {
            runTest {
                // given
                val givenChat = ChatRef(id = randomUUID())
                val givenEvents = listOf(DebugEvent(chat = givenChat), DebugEvent(chat = givenChat))
                val givenMembers = listOf(UserRef(id = randomUUID()))

                every { usersRepo.members(listOf(givenChat.id)) } returns flowOf(mapOf(givenChat.id to givenMembers))

                // when
                launch {
                    givenEvents.forEach { event ->
                        delay(251)
                        target.handleEvent(event)
                        delay(251)
                    }
                }
                val events = target.events().take(2).toList()

                // then
                events shouldHaveSize 2
                events.flatMap { it } shouldBe givenEvents.map { it + givenMembers }
            }
        }

        "keep the flow empty when no events are sent" {
            runTest {
                // when
                var ticks = 0
                val job = launch {
                    target.events().collect { ticks++ }
                }
                advanceTimeBy(windowSize.toKotlinDuration() - 1.milliseconds)
                job.cancel()

                // then
                ticks.shouldBeZero()
            }
        }
    }
})

private operator fun ChatLifecycleEvent.plus(members: Collection<UserRef>) = this.withMembers(members)

private fun ChatLifecycleEvent.withMembers(members: Collection<UserRef>) = ChatEventWithMembers(this, members)
