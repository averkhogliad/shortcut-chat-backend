package io.averkhoglyad.shortcut.notification.config

import io.averkhoglyad.shortcut.notification.data.ChatLifecycleEvent
import io.averkhoglyad.shortcut.notification.kafka.ChatLifecycleTypeResolver
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.kafka.autoconfigure.ConcurrentKafkaListenerContainerFactoryConfigurer
import org.springframework.boot.kafka.autoconfigure.KafkaProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.config.ContainerCustomizer
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer

const val KAFKA_LISTENER_CHAT_LIFECYCLE = "KAFKA_LISTENER_CHAT_LIFECYCLE"

@Configuration
class KafkaConfig(
    private val configurer: ConcurrentKafkaListenerContainerFactoryConfigurer,
    private val kafkaContainerCustomizer: ObjectProvider<ContainerCustomizer<Any, Any, ConcurrentMessageListenerContainer<Any, Any>>>,
    private val kafkaProperties: KafkaProperties,
    private val chatLifecycleTypeResolver: ChatLifecycleTypeResolver,
) {

    @Bean(KAFKA_LISTENER_CHAT_LIFECYCLE)
    fun taskLifecycleKafkaListenerContainerFactory(): ConcurrentKafkaListenerContainerFactory<String, ChatLifecycleEvent> {
        val consumerFactory = chatLifecycleConsumerFactory()
        return kafkaListenerContainerFactory(consumerFactory)
    }

    private fun chatLifecycleConsumerFactory(): ConsumerFactory<String, ChatLifecycleEvent> = consumerFactory {
        val jsonDeserializer = JacksonJsonDeserializer<ChatLifecycleEvent>()
        jsonDeserializer.setUseTypeHeaders(false)
        jsonDeserializer.setTypeResolver(chatLifecycleTypeResolver)
        setValueDeserializerSupplier { jsonDeserializer }
    }

    private inline fun <K : Any, V : Any> consumerFactory(crossinline cb: DefaultKafkaConsumerFactory<K, V>.() -> Unit = {}): ConsumerFactory<K, V> {
        val consumerFactory = DefaultKafkaConsumerFactory<K, V>(kafkaProperties.buildConsumerProperties())
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
