package io.averkhoglyad.shortcut.chat.config

import io.averkhoglyad.shortcut.chat.core.model.UserCreatedPayload
import io.averkhoglyad.shortcut.chat.core.model.UserDeletedPayload
import io.averkhoglyad.shortcut.chat.core.model.UserUpdatedPayload
import io.averkhoglyad.shortcut.chat.kafka.UserLifecycleTypeResolver
import org.springframework.beans.factory.ObjectProvider
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.springframework.boot.autoconfigure.kafka.ConcurrentKafkaListenerContainerFactoryConfigurer
import org.springframework.boot.autoconfigure.kafka.KafkaConnectionDetails
import org.springframework.boot.autoconfigure.kafka.KafkaProperties
import org.springframework.boot.ssl.SslBundles
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.config.ContainerCustomizer
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer
import org.springframework.kafka.support.serializer.JsonDeserializer

const val KAFKA_LISTENER_USER_LIFECYCLE = "KAFKA_LISTENER_USER_LIFECYCLE"

@Configuration
class KafkaConfig(
    private val configurer: ConcurrentKafkaListenerContainerFactoryConfigurer,
    private val kafkaContainerCustomizer: ObjectProvider<ContainerCustomizer<Any?, Any?, ConcurrentMessageListenerContainer<Any?, Any?>?>>,
    private val kafkaProperties: KafkaProperties,
    private val kafkaConnectionDetails: KafkaConnectionDetails,
    private val userLifecycleTypeResolver: UserLifecycleTypeResolver,
    private val sslBundles: SslBundles,
) {

    @Bean(KAFKA_LISTENER_USER_LIFECYCLE)
    fun userLifecycleKafkaListenerContainerFactory(): ConcurrentKafkaListenerContainerFactory<String, Any> {
        val consumerFactory = userLifecycleConsumerFactory()
        return kafkaListenerContainerFactory(consumerFactory)
    }

    private fun userLifecycleConsumerFactory(): ConsumerFactory<String, Any> = consumerFactory {
        val jsonDeserializer = JsonDeserializer<Any>()
        jsonDeserializer.setUseTypeHeaders(false)
        jsonDeserializer.setTypeResolver(userLifecycleTypeResolver)
        setValueDeserializerSupplier { jsonDeserializer }
    }

    private inline fun <K, V> consumerFactory(crossinline cb: DefaultKafkaConsumerFactory<K, V>.() -> Unit = {}): ConsumerFactory<K, V> {
        val properties = kafkaProperties.buildConsumerProperties(sslBundles)
        properties[ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG] = kafkaConnectionDetails.consumerBootstrapServers
        val consumerFactory = DefaultKafkaConsumerFactory<K, V>(properties)
        consumerFactory.cb()
        return consumerFactory
    }

    private fun <K, V> kafkaListenerContainerFactory(consumerFactory: ConsumerFactory<K, V>): ConcurrentKafkaListenerContainerFactory<K, V> {
        val factory = ConcurrentKafkaListenerContainerFactory<K, V>()
        @Suppress("UNCHECKED_CAST")
        configurer
            .configure(
                factory as ConcurrentKafkaListenerContainerFactory<Any?, Any?>,
                consumerFactory as ConsumerFactory<Any?, Any?>
            )
        kafkaContainerCustomizer
            .ifAvailable { factory.setContainerCustomizer(it) }
        return factory
    }
}
