package dev.ihaletakip;

import dev.ihaletakip.config.IhaleProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(IhaleProperties.class)
public class IhaleTakipApplication {
    public static void main(String[] args) {
        SpringApplication.run(IhaleTakipApplication.class, args);
    }
}
