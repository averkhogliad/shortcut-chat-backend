package io.averkhoglyad.shortcut.chat.config

import com.fasterxml.uuid.Generators
import io.averkhoglyad.shortcut.common.persistence.id.IdGenerationBeforeConvertCallback
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.data.jdbc.repository.config.EnableJdbcAuditing
import org.springframework.data.relational.auditing.RelationalAuditingCallback
import org.springframework.jdbc.core.simple.JdbcClient
import javax.sql.DataSource
import java.util.*

@Configuration
@EnableJdbcAuditing
class PersistenceConfig {

    @Bean
    @ConditionalOnMissingBean
    fun jdbcClient(dataSource: DataSource): JdbcClient = JdbcClient.create(dataSource)

    @Bean
    @Order(RelationalAuditingCallback.AUDITING_ORDER + 10)
    fun idGenerationCallback(): IdGenerationBeforeConvertCallback<UUID> {
        val uuidGenerator = Generators.timeBasedEpochGenerator()
        return IdGenerationBeforeConvertCallback(uuidGenerator::generate)
    }
}
