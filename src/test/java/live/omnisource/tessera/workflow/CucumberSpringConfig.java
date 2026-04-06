package live.omnisource.tessera.workflow;

import io.cucumber.spring.CucumberContextConfiguration;
import live.omnisource.tessera.support.IntegrationTestBase;

/**
 * Binds Cucumber to the Spring Boot test context.
 * Extends IntegrationTestBase to reuse the Testcontainers PostgreSQL instance.
 */
@CucumberContextConfiguration
public class CucumberSpringConfig extends IntegrationTestBase {
}