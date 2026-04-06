package live.omnisource.tessera;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import live.omnisource.tessera.support.IntegrationTestBase;

/**
 * Smoke test — verifies the full Spring context loads with Testcontainers DB, Flyway migrations
 * run, and essential beans are present.
 */
class TesseraApplicationTests extends IntegrationTestBase {

  @Autowired ApplicationContext ctx;

  @Test
  void contextLoads() {
    assertThat(ctx).isNotNull();
  }

  @Test
  void essentialBeansArePresent() {
    assertThat(ctx.containsBean("queryCatalogService")).isTrue();
    assertThat(ctx.containsBean("workspaceService")).isTrue();
    assertThat(ctx.containsBean("dataStoreService")).isTrue();
    assertThat(ctx.containsBean("apiKeyService")).isTrue();
    assertThat(ctx.containsBean("streamBroker")).isTrue();
  }

  @Test
  void connectionFactoriesMapHasFiveConnectors() {
    var factories = ctx.getBean("connectionFactories", java.util.Map.class);
    assertThat(factories).containsKeys("postgis", "cassandra", "mysql", "oracle", "elasticsearch");
  }
}
