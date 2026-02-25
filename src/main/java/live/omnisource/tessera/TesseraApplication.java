package live.omnisource.tessera;

import live.omnisource.tessera.config.ApplicationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Entry point for the Tessera application. This class is responsible for bootstrapping
 * the application using Spring Boot framework.
 * The application is configured to load properties defined in the {@code ApplicationProperties}
 * class and its nested records. These properties include configurations related to H3, Sync,
 * Catalog, and Crypto.
 * Annotations:
 * - {@code @SpringBootApplication}: Marks this class as a Spring Boot application entry point.
 * - {@code @EnableConfigurationProperties}: Enables the binding of configuration properties
 *   to the {@code ApplicationProperties} class.
 * Usage:
 * The application can be started by invoking the {@code main} method, which delegates to
 * {@code SpringApplication.run}.
 */
@SpringBootApplication
@EnableConfigurationProperties({ApplicationProperties.class})
public class TesseraApplication {

    static void main(String[] args) {
        SpringApplication.run(TesseraApplication.class, args);
    }

}
