package io.averkhoglyad.shortcut.outbox.autoconfigure

import com.fasterxml.jackson.databind.ObjectMapper
import io.averkhoglyad.shortcut.outbox.core.OutboxCleaner
import io.averkhoglyad.shortcut.outbox.core.OutboxCleanerConfig
import io.averkhoglyad.shortcut.outbox.core.OutboxCleanerImpl
import io.averkhoglyad.shortcut.outbox.core.OutboxLeaseConfig
import io.averkhoglyad.shortcut.outbox.core.OutboxRelay
import io.averkhoglyad.shortcut.outbox.core.OutboxRelayConfig
import io.averkhoglyad.shortcut.outbox.core.OutboxRelayImpl
import io.averkhoglyad.shortcut.outbox.core.OutboxRetryPolicy
import io.averkhoglyad.shortcut.outbox.core.OutboxWriter
import io.averkhoglyad.shortcut.outbox.core.OutboxWriterImpl
import io.averkhoglyad.shortcut.outbox.messaging.OutboxPublisher
import io.averkhoglyad.shortcut.outbox.persistence.OutboxDao
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration
import org.springframework.boot.autoconfigure.jdbc.JdbcClientAutoConfiguration
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler

@AutoConfiguration(
    after = [
        JdbcClientAutoConfiguration::class,
        JacksonAutoConfiguration::class,
        KafkaAutoConfiguration::class,
    ]
)
@EnableConfigurationProperties(TxOutboxProperties::class)
class TxOutboxAutoConfiguration {

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnBean(JdbcClient::class, ObjectMapper::class)
    class WritePathConfiguration {

        @Bean
        @ConditionalOnMissingBean
        fun outboxDao(jdbcClient: JdbcClient, objectMapper: ObjectMapper): OutboxDao =
            OutboxDao(jdbcClient, objectMapper)

        @Bean
        @ConditionalOnMissingBean
        fun outboxWriter(dao: OutboxDao): OutboxWriter = OutboxWriterImpl(dao)

        @Bean
        @ConditionalOnMissingBean
        fun outboxRouter(properties: TxOutboxProperties): OutboxRouter =
            ConfigurationBackedOutboxRouter(properties)

        @Bean
        @ConditionalOnMissingBean
        fun outboxHeaderProvider(): OutboxHeaderProvider = DefaultOutboxHeaderProvider()

        @Bean
        @ConditionalOnMissingBean
        fun outboxEmitter(
            router: OutboxRouter,
            headerProvider: OutboxHeaderProvider,
            writer: OutboxWriter,
            objectMapper: ObjectMapper,
            @Value("\${spring.application.name:application}") applicationName: String,
        ): OutboxEmitter = OutboxEmitterImpl(
            router = router,
            headerProvider = headerProvider,
            writer = writer,
            objectMapper = objectMapper,
            applicationName = applicationName,
        )
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "tx-outbox", name = ["enabled"], havingValue = "true")
    @ConditionalOnBean(JdbcClient::class, ObjectMapper::class)
    @ConditionalOnOutboxPublisher
    @Import(TxOutboxScheduleConfig::class)
    class RuntimeConfiguration {

        @Bean
        @ConditionalOnMissingBean(OutboxPublisher::class)
        @ConditionalOnBean(KafkaTemplate::class)
        fun kafkaTemplateOutboxPublisher(
            kafkaTemplate: KafkaTemplate<String, ByteArray>,
        ): OutboxPublisher = KafkaTemplateOutboxPublisher(kafkaTemplate)

        @Bean
        @ConditionalOnMissingBean
        fun outboxRelay(
            dao: OutboxDao,
            publisher: OutboxPublisher,
            properties: TxOutboxProperties,
            @Value("\${spring.application.name:application}") applicationName: String,
        ): OutboxRelay = OutboxRelayImpl(
            dao = dao,
            publisher = publisher,
            relayConfig = OutboxRelayConfig(
                batchSize = properties.relay.batchSize,
                maxLoopsPerRun = properties.relay.maxLoopsPerRun,
            ),
            leaseConfig = OutboxLeaseConfig(
                owner = properties.lease.owner.ifBlank { applicationName },
                duration = properties.lease.duration,
            ),
            retryPolicy = OutboxRetryPolicy(
                initialDelay = properties.retry.initialDelay,
                multiplier = properties.retry.multiplier,
                maxDelay = properties.retry.maxDelay,
                maxAttempts = properties.retry.maxAttempts,
            ),
        )

        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnProperty(
            prefix = "tx-outbox.cleanup",
            name = ["enabled"],
            havingValue = "true",
            matchIfMissing = true,
        )
        fun outboxCleaner(dao: OutboxDao, properties: TxOutboxProperties): OutboxCleaner =
            OutboxCleanerImpl(
                dao = dao,
                config = OutboxCleanerConfig(
                    publishedRetention = properties.cleanup.publishedRetention,
                    deadRetention = properties.cleanup.deadRetention,
                ),
            )

        @Bean("outboxTaskScheduler")
        @ConditionalOnMissingBean(name = ["outboxTaskScheduler"])
        fun outboxTaskScheduler(): ThreadPoolTaskScheduler =
            ThreadPoolTaskScheduler().apply {
                poolSize = 2
                setThreadNamePrefix("outbox-")
                setWaitForTasksToCompleteOnShutdown(true)
            }

        @Bean
        @ConditionalOnMissingBean(MeterRegistry::class)
        fun outboxMeterRegistry(): MeterRegistry = SimpleMeterRegistry()

        @Bean
        @ConditionalOnMissingBean
        fun outboxMetrics(dao: OutboxDao, registry: MeterRegistry): OutboxMetrics =
            OutboxMetrics(dao).also { it.bindTo(registry) }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "tx-outbox", name = ["enabled"], havingValue = "true")
    class ActivationReporterConfiguration {

        @Bean
        @ConditionalOnMissingBean
        fun outboxActivationReporter(
            jdbcClient: ObjectProvider<JdbcClient>,
            objectMapper: ObjectProvider<ObjectMapper>,
            publisher: ObjectProvider<OutboxPublisher>,
        ): OutboxActivationReporter = OutboxActivationReporter(
            jdbcClient = jdbcClient,
            objectMapper = objectMapper,
            publisher = publisher,
        )
    }
}
