package com.ledger;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Singleton containers for the whole test JVM: started once in a static
 * block, shared by every IT, reused across runs when the local Testcontainers
 * config has {@code testcontainers.reuse.enable=true}.
 *
 * This service only needs Postgres; add further containers (Redis, Kafka, ...)
 * as static fields here when the service grows a dependency on them.
 */
public abstract class TestContainerConfig {

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine").withReuse(true);

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
