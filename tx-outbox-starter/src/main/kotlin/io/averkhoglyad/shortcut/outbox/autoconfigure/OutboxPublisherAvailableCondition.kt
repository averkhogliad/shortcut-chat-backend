package io.averkhoglyad.shortcut.outbox.autoconfigure

import io.averkhoglyad.shortcut.outbox.messaging.OutboxPublisher
import org.springframework.boot.autoconfigure.condition.ConditionMessage
import org.springframework.boot.autoconfigure.condition.ConditionOutcome
import org.springframework.boot.autoconfigure.condition.SpringBootCondition
import org.springframework.context.annotation.Condition
import org.springframework.context.annotation.Conditional
import org.springframework.context.annotation.ConditionContext
import org.springframework.context.annotation.ConfigurationCondition
import org.springframework.core.type.AnnotatedTypeMetadata
import org.springframework.kafka.core.KafkaTemplate

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@Conditional(OutboxPublisherAvailableCondition::class)
annotation class ConditionalOnOutboxPublisher

class OutboxPublisherAvailableCondition : SpringBootCondition(), ConfigurationCondition {

    override fun getConfigurationPhase(): ConfigurationCondition.ConfigurationPhase =
        ConfigurationCondition.ConfigurationPhase.REGISTER_BEAN

    override fun getMatchOutcome(
        context: ConditionContext,
        metadata: AnnotatedTypeMetadata,
    ): ConditionOutcome {
        val beanFactory = context.beanFactory
            ?: return ConditionOutcome.noMatch(
                ConditionMessage.forCondition(ConditionalOnOutboxPublisher::class.java)
                    .because("no bean factory is available")
            )
        val hasPublisher = beanFactory
            .getBeanNamesForType(OutboxPublisher::class.java, true, false)
            .isNotEmpty()
        val hasKafkaTemplate = beanFactory
            .getBeanNamesForType(KafkaTemplate::class.java, true, false)
            .isNotEmpty()
        return if (hasPublisher || hasKafkaTemplate) {
            ConditionOutcome.match("an OutboxPublisher or KafkaTemplate bean is available")
        } else {
            ConditionOutcome.noMatch(
                ConditionMessage.forCondition(ConditionalOnOutboxPublisher::class.java)
                    .because("no OutboxPublisher bean and no KafkaTemplate bean are available")
            )
        }
    }
}
