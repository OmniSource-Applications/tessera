package live.omnisource.tessera.workflow.steps;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.*;

import org.springframework.beans.factory.annotation.Autowired;

import io.cucumber.datatable.DataTable;
import io.cucumber.java.After;
import io.cucumber.java.en.*;
import live.omnisource.tessera.catalog.QueryCatalogService;
import live.omnisource.tessera.catalog.QueryExecutor;
import live.omnisource.tessera.catalog.entity.CatalogEntry;

public class QueryWorkflowSteps {

  @Autowired QueryCatalogService catalogService;

  @Autowired QueryExecutor executor;

  private final List<String> createdQueries = new ArrayList<>();
  private QueryExecutor.QueryResult lastResult;
  private Exception lastError;

  @After
  public void cleanup() {
    createdQueries.forEach(
        name -> {
          try {
            catalogService.findByName(name).ifPresent(e -> catalogService.delete(e.getId()));
          } catch (Exception ignored) {
          }
        });
    createdQueries.clear();
    lastResult = null;
    lastError = null;
  }

  @When("I create a catalog query {string} with SQL {string}")
  public void createQuery(String name, String sql) {
    var entry = new CatalogEntry();
    entry.setName(name);
    entry.setCategory("CUSTOM");
    entry.setQuerySql(sql);
    entry.setTimeoutMs(5000);
    entry.setTags(List.of("bdd-test"));

    catalogService.create(entry);
    createdQueries.add(name);
  }

  @Given("I create a catalog query {string} with SQL {string} and params:")
  public void createQueryWithParams(String name, String sql, DataTable table) {
    var entry = new CatalogEntry();
    entry.setName(name);
    entry.setCategory("CUSTOM");
    entry.setQuerySql(sql);
    entry.setTimeoutMs(5000);
    entry.setTags(List.of("bdd-test"));

    Map<String, Object> properties = new LinkedHashMap<>();
    for (var row : table.asMaps()) {
      Map<String, Object> prop = new HashMap<>();
      prop.put("type", row.get("type"));
      if (row.get("default") != null && !row.get("default").isBlank()) {
        prop.put("default", row.get("default"));
      }
      properties.put(row.get("name"), prop);
    }
    entry.setParamSchema(Map.of("type", "object", "properties", properties));

    catalogService.create(entry);
    createdQueries.add(name);
  }

  @When("I try to create a catalog query {string} with SQL {string}")
  public void tryCreateQuery(String name, String sql) {
    try {
      createQuery(name, sql);
    } catch (Exception e) {
      lastError = e;
    }
  }

  @When("I execute the query {string}")
  public void executeQuery(String name) {
    var entry = catalogService.getByName(name);
    lastResult = executor.execute(entry, Map.of());
  }

  @When("I execute the query {string} with parameters:")
  public void executeQueryWithParams(String name, DataTable table) {
    var entry = catalogService.getByName(name);
    Map<String, Object> params = new HashMap<>();
    table.asMap().forEach(params::put);
    lastResult = executor.execute(entry, params);
  }

  @When("I delete the query {string}")
  public void deleteQuery(String name) {
    var entry = catalogService.getByName(name);
    catalogService.delete(entry.getId());
    createdQueries.remove(name);
  }

  @Then("the query {string} should exist in the catalog")
  public void queryShouldExist(String name) {
    assertThat(catalogService.findByName(name)).isPresent();
  }

  @Then("the query {string} should not exist in the catalog")
  public void queryShouldNotExist(String name) {
    assertThat(catalogService.findByName(name)).isEmpty();
  }

  @Then("the query execution should succeed")
  public void executionSucceeded() {
    assertThat(lastResult).isNotNull();
    assertThat(lastResult.success()).isTrue();
  }

  @Then("the result should contain {int} row(s)")
  public void resultRowCount(int count) {
    assertThat(lastResult.rowCount()).isEqualTo(count);
  }

  @Then("the result should contain a row where {string} equals {int}")
  public void resultContainsValue(String column, int expected) {
    assertThat(lastResult.rows())
        .anySatisfy(row -> assertThat(((Number) row.get(column)).intValue()).isEqualTo(expected));
  }

  @Then("I should receive a validation error about read-only queries")
  public void readOnlyError() {
    assertThat(lastError).isNotNull();
    assertThat(lastError.getMessage()).containsIgnoringCase("read-only");
  }
}
