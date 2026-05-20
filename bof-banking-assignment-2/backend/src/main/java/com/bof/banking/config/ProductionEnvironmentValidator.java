package com.bof.banking.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Validates critical runtime properties before the app starts serving traffic.
 * Active only for the production profile.
 */
@Component
@Profile("prod")
public class ProductionEnvironmentValidator implements ApplicationRunner {

    private final Environment environment;

    /**
     * Creates a new ProductionEnvironmentValidator instance.
     * @param environment the environment.
     */
    public ProductionEnvironmentValidator(Environment environment) {
        this.environment = environment;
    }

    @Override
    /**
     * Handles run.
     * @param args application startup arguments.
     */
    public void run(ApplicationArguments args) {
        List<String> missingProperties = new ArrayList<>();

        requireNonBlank("spring.datasource.url", missingProperties);
        requireNonBlank("spring.datasource.username", missingProperties);
        requireNonBlank("spring.datasource.password", missingProperties);
        requireNonBlank("jwt.secret", missingProperties);
        requireNonBlank("app.cors.allowed-origins", missingProperties);

        if (!missingProperties.isEmpty()) {
            throw new IllegalStateException(
                    "Missing required production configuration values: "
                            + String.join(", ", missingProperties)
                            + ". Ensure environment variables are set (for example: "
                            + "SPRING_DATASOURCE_URL, SPRING_DATASOURCE_USERNAME, "
                            + "SPRING_DATASOURCE_PASSWORD, JWT_SECRET, APP_CORS_ALLOWED_ORIGINS)."
            );
        }
    }

    @SuppressWarnings("null")
    private void requireNonBlank(String propertyName, List<String> missingProperties) {
        String value = Objects.requireNonNullElse(environment.getProperty(propertyName), "");
        if (!StringUtils.hasText(value)) {
            missingProperties.add(propertyName);
        }
    }
}
