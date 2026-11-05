package io.averkhoglyad.shortcut.message.config

import io.averkhoglyad.shortcut.message.kafka.ChatLifecycleTypeResolver
import io.averkhoglyad.shortcut.message.kafka.UserLifecycleTypeResolver
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.kafka.autoconfigure.ConcurrentKafkaListenerContainerFactoryConfigurer
import org.springframework.boot.kafka.autoconfigure.KafkaConnectionDetails
import org.springframework.boot.kafka.autoconfigure.KafkaProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.config.ContainerCustomizer
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer

const val KAFKA_LISTENER_USER_LIFECYCLE = "KAFKA_LISTENER_USER_LIFECYCLE"
const val KAFKA_LISTENER_CHAT_LIFECYCLE = "KAFKA_LISTENER_CHAT_LIFECYCLE"

@Configuration
class KafkaConfig(
    private val configurer: ConcurrentKafkaListenerContainerFactoryConfigurer,
    private val kafkaContainerCustomizer: ObjectProvider<ContainerCustomizer<Any, Any, ConcurrentMessageListenerContainer<Any, Any>>>,
    private val kafkaProperties: KafkaProperties,
    private val kafkaConnectionDetails: KafkaConnectionDetails,
    private val userLifecycleTypeResolver: UserLifecycleTypeResolver,
    private val chatLifecycleTypeResolver: ChatLifecycleTypeResolver,
) {

    @Bean(KAFKA_LISTENER_USER_LIFECYCLE)
    fun userLifecycleKafkaListenerContainerFactory(): ConcurrentKafkaListenerContainerFactory<String, Any> {
        val consumerFactory = userLifecycleConsumerFactory()
        return kafkaListenerContainerFactory(consumerFactory)
    }

    @Bean(KAFKA_LISTENER_CHAT_LIFECYCLE)
    fun chatLifecycleKafkaListenerContainerFactory(): ConcurrentKafkaListenerContainerFactory<String, Any> {
        val consumerFactory = chatLifecycleConsumerFactory()
        return kafkaListenerContainerFactory(consumerFactory)
    }

    private fun userLifecycleConsumerFactory(): ConsumerFactory<String, Any> = consumerFactory {
        val jsonDeserializer = JacksonJsonDeserializer<Any>()
        jsonDeserializer.setUseTypeHeaders(false)
        jsonDeserializer.setTypeResolver(userLifecycleTypeResolver)
        setValueDeserializerSupplier { jsonDeserializer }
    }

    private fun chatLifecycleConsumerFactory(): ConsumerFactory<String, Any> = consumerFactory {
        val jsonDeserializer = JacksonJsonDeserializer<Any>()
        jsonDeserializer.setUseTypeHeaders(false)
        jsonDeserializer.setTypeResolver(chatLifecycleTypeResolver)
        setValueDeserializerSupplier { jsonDeserializer }
    }

    private inline fun <K : Any, V : Any> consumerFactory(crossinline cb: DefaultKafkaConsumerFactory<K, V>.() -> Unit = {}): ConsumerFactory<K, V> {
        val properties = kafkaProperties.buildConsumerProperties()
        properties[ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG] = kafkaConnectionDetails.consumer.bootstrapServers
        val consumerFactory = DefaultKafkaConsumerFactory<K, V>(properties)
        consumerFactory.cb()
        return consumerFactory
    }

    private fun <K : Any, V : Any> kafkaListenerContainerFactory(consumerFactory: ConsumerFactory<K, V>): ConcurrentKafkaListenerContainerFactory<K, V> {
        val factory = ConcurrentKafkaListenerContainerFactory<K, V>()
        @Suppress("UNCHECKED_CAST")
        configurer
            .configure(
                factory as ConcurrentKafkaListenerContainerFactory<Any, Any>,
                consumerFactory as ConsumerFactory<Any, Any>
            )
        @Suppress("UNCHECKED_CAST")
        val customizer = kafkaContainerCustomizer.getIfAvailable()
            as ContainerCustomizer<K, V, ConcurrentMessageListenerContainer<K, V>>?
        if (customizer != null) {
            factory.setContainerCustomizer(customizer)
        }
        return factory
    }
}
