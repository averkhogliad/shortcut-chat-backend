package io.averkhoglyad.shortcut.users.config

import net.javacrumbs.shedlock.core.LockProvider
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.EnableScheduling

@Configuration
@EnableScheduling
@EnableSchedulerLock(
    defaultLockAtLeastFor = "\${scheduler.default-lock.lock-at-least-for:PT0S}",
    defaultLockAtMostFor = "\${scheduler.default-lock.lock-at-most-for:PT10M}"
)
@Profile("!test")
class ScheduleConfig {

    @Bean
    fun lockProvider(jdbc: JdbcTemplate): LockProvider = JdbcTemplateLockProvider(jdbc)
}
