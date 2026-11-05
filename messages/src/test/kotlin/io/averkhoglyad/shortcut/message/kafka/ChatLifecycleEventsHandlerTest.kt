package io.averkhoglyad.shortcut.message.kafka

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.averkhoglyad.shortcut.message.core.model.ChatCreatedPayload
import io.averkhoglyad.shortcut.message.core.service.ChatProjectionService
import io.averkhoglyad.shortcut.message.core.service.chatCreatedPayloads
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.property.Arb
import io.kotest.property.arbitrary.next
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.*

class ChatLifecycleEventsHandlerTest : FreeSpec({

    val service = mockk<ChatProjectionService>()
    val handler = ChatLifecycleEventsHandler(service)

    afterTest {
        clearAllMocks()
    }

    "handleCreated" - {
        "applies projection with event time from published-at header" {
            // given
            val payload = Arb.chatCreatedPayloads().next()
            val eventTime = Instant.parse("2025-06-01T12:00:00Z")
            val idSlot = slot<UUID>()
            val timeSlot = slot<Instant>()
            val payloadSlot = slot<ChatCreatedPayload>()
            every { service.applyCreated(any(), any(), any()) } returns Unit

            // when
            handler.handleCreated(payload, eventTime.toString())

            // then
            verify {
                service.applyCreated(capture(idSlot), capture(timeSlot), capture(payloadSlot))
            }
            idSlot.captured shouldBe payload.id
            timeSlot.captured shouldBe eventTime
            payloadSlot.captured shouldBe payload
        }
    }

    "handleUnknown" - {
        "logs unknown event and does not call projection service" {
            // given
            val appender = ListAppender<ILoggingEvent>()
            val logger = LoggerFactory.getLogger(ChatLifecycleEventsHandler::class.java) as Logger
            logger.addAppender(appender)
            appender.start()

            // when
            try {
                handler.handleUnknown(mapOf("foo" to "bar"), "ChatUpdated")
            } finally {
                logger.detachAppender(appender)
                appender.stop()
            }

            // then
            appender.list.shouldHaveSize(1)
            appender.list.first().level shouldBe Level.WARN
            appender.list.first().formattedMessage shouldContain "ChatUpdated"
            verify(exactly = 0) { service.applyCreated(any(), any(), any()) }
        }
    }
})
