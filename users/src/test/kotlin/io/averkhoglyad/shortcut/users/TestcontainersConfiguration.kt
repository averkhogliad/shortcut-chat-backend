package io.averkhoglyad.shortcut.users

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.containers.GenericContainer
import org.testcontainers.kafka.ConfluentKafkaContainer
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

private const val POSTGRES_IMAGE = "postgres:14-alpine"
private const val REDIS_IMAGE = "redis:7.4.2"
private const val REDIS_PORT = 6379
private const val KAFKA_IMAGE = "confluentinc/cp-kafka:7.8.0"

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    fun postgresContainer(): PostgreSQLContainer = PostgreSQLContainer(DockerImageName.parse(POSTGRES_IMAGE))

    @Bean
    @ServiceConnection("redis")
    fun redisContainer(): GenericContainer<*> = GenericContainer(DockerImageName.parse(REDIS_IMAGE))
        .withExposedPorts(REDIS_PORT)

    @Bean
    @ServiceConnection
    fun kafkaContainer(): ConfluentKafkaContainer = ConfluentKafkaContainer(DockerImageName.parse(KAFKA_IMAGE))
        .withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "true")
        .withEnv("KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR", "1")
        .withEnv("KAFKA_MIN_INSYNC_REPLICAS", "1")
        .withEnv("KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR", "1")
        .withEnv("KAFKA_TRANSACTION_STATE_LOG_MIN_ISR", "1")
}
