package io.averkhoglyad.shortcut.mail.sender.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {

    @Bean
    public Counter sentMailCounter(MeterRegistry meterRegistry) {
        return Counter.builder("sent_emails")
                .description("Sent emails count")
                .register(meterRegistry);
    }
}
