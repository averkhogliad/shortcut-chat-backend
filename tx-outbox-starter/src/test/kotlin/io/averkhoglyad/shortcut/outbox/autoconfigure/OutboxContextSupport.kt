package io.averkhoglyad.shortcut.outbox.autoconfigure

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger as LogbackLogger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.averkhoglyad.shortcut.outbox.core.OutboxEntry
import org.slf4j.LoggerFactory
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.ConfigurableApplicationContext
import java.time.Duration
import java.util.UUID

internal fun outboxRunner(): ApplicationContextRunner =
    ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(TxOutboxAutoConfiguration::class.java))

internal fun kafkaOutboxRunner(): ApplicationContextRunner =
    ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                KafkaAutoConfiguration::class.java,
                TxOutboxAutoConfiguration::class.java,
            )
        )

internal fun outboxEntry(): OutboxEntry = OutboxEntry(
    topic = "UserLifecycle",
    key = "user-1",
    body = "{}",
    headers = mapOf("X-Event-Name" to "UserCreated"),
    id = UUID.fromString("018f2b5e-0000-7000-8000-000000000001"),
)

internal fun ConfigurableApplicationContext.publishReadyEvent() {
    publishEvent(ApplicationReadyEvent(SpringApplication(), emptyArray(), this, Duration.ZERO))
}

internal fun captureWarnings(action: () -> Unit): List<ILoggingEvent> {
    val logger = LoggerFactory.getLogger(OutboxActivationReporter::class.java) as LogbackLogger
    val appender = ListAppender<ILoggingEvent>().apply {
        context = logger.loggerContext
        start()
    }
    logger.addAppender(appender)
    try {
        action()
    } finally {
        logger.detachAppender(appender)
        appender.stop()
    }
    return appender.list.filter { it.level == Level.WARN }
}
