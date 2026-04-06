package live.omnisource.tessera.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for integration tests that need a real PostgreSQL database with PostGIS, H3, and
 * pg_partman extensions.
 *
 * <p>Uses a shared Testcontainer instance to avoid starting a new DB per test class. The container
 * image must have PostGIS, H3, and pg_partman pre-installed. Flyway migrations run on first
 * connect.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
public abstract class IntegrationTestBase {

  static final PostgreSQLContainer<?> POSTGRES;

  static {
    var image =
        new org.testcontainers.images.builder.ImageFromDockerfile("tessera-test-postgres", false)
            .withFileFromPath(".", java.nio.file.Paths.get("ops/tessera/docker/postgres"));

    POSTGRES =
        new PostgreSQLContainer<>(
                DockerImageName.parse(image.get()).asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("tessera")
            .withUsername("tessera")
            .withPassword("tessera")
            .withLogConsumer(
                new org.testcontainers.containers.output.Slf4jLogConsumer(
                    org.slf4j.LoggerFactory.getLogger("tc.postgres")))
            .waitingFor(org.testcontainers.containers.wait.strategy.Wait.forListeningPort());

    POSTGRES.start();
  }

  @DynamicPropertySource
  static void postgresProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }
}
