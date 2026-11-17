package dev.ihaletakip.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ZamanConfig {
    public static final ZoneId TR = ZoneId.of("Europe/Istanbul");

    @Bean
    public Clock clock() {
        return Clock.system(TR);
    }
}
