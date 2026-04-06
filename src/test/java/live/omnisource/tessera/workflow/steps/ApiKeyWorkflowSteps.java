package live.omnisource.tessera.workflow.steps;

import io.cucumber.java.After;
import io.cucumber.java.en.*;
import live.omnisource.tessera.apikey.ApiKeyFeatureToggle;
import live.omnisource.tessera.apikey.ApiKeyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.*;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

public class ApiKeyWorkflowSteps {

    @Autowired
    ApiKeyService apiKeyService;

    @Autowired
    ApiKeyFeatureToggle featureToggle;

    @Autowired
    TestRestTemplate restTemplate;

    private final Map<String, String> createdRawKeys = new LinkedHashMap<>();
    private final List<UUID> createdKeyIds = new ArrayList<>();
    private String lastRawKey;
    private ResponseEntity<?> lastApiResponse;

    @After
    public void cleanup() {
        createdKeyIds.forEach(id -> {
            try { apiKeyService.delete(id); } catch (Exception ignored) {}
        });
        createdKeyIds.clear();
        createdRawKeys.clear();
        lastRawKey = null;
        lastApiResponse = null;
    }

    @Given("the API key feature is enabled")
    public void enableFeature() {
        if (!featureToggle.isEnabled()) featureToggle.enable();
    }

    @Given("the API key feature is disabled")
    public void disableFeature() {
        if (featureToggle.isEnabled()) featureToggle.disable();
    }

    @When("I create an API key named {string} with scopes {string}")
    public void createKeyWithScopes(String name, String scopes) {
        var scopeList = Arrays.asList(scopes.split(","));
        var result = apiKeyService.create(name, "admin", scopeList, 60, null);
        lastRawKey = result.rawKey();
        createdRawKeys.put(name, result.rawKey());
        createdKeyIds.add(result.entity().getId());
    }

    @Given("I have created an API key named {string}")
    public void haveCreatedKey(String name) {
        var result = apiKeyService.create(name, "admin",
                List.of("QUERY_READ", "QUERY_EXECUTE"), 60, null);
        lastRawKey = result.rawKey();
        createdRawKeys.put(name, result.rawKey());
        createdKeyIds.add(result.entity().getId());
    }

    @When("I call the query API with the generated key")
    public void callApiWithKey() {
        var headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + lastRawKey);
        var entity = new HttpEntity<>(headers);

        lastApiResponse = restTemplate.exchange(
                "/api/queries", HttpMethod.GET, entity, String.class);
    }

    @When("I revoke the key {string}")
    public void revokeKey(String name) {
        var keys = apiKeyService.listAll();
        var match = keys.stream()
                .filter(k -> k.getName().equals(name))
                .findFirst()
                .orElseThrow();
        apiKeyService.revoke(match.getId());
    }

    @When("I try to create an API key via the API")
    public void tryCreateKeyViaApi() {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBasicAuth("admin", "admin");
        var body = "{\"name\":\"api-test\",\"scopes\":[\"QUERY_READ\"]}";

        lastApiResponse = restTemplate.exchange(
                "/api/keys", HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);
    }

    @Then("I should receive a raw key starting with {string}")
    public void rawKeyStartsWith(String prefix) {
        assertThat(lastRawKey).startsWith(prefix);
    }

    @Then("the key {string} should appear in the key list")
    public void keyInList(String name) {
        var keys = apiKeyService.listAll();
        assertThat(keys).anyMatch(k -> k.getName().equals(name));
    }

    @Then("the API request should succeed")
    public void apiRequestSucceeded() {
        assertThat(lastApiResponse).isNotNull();
        assertThat(lastApiResponse.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Then("the key {string} should be marked as revoked")
    public void keyIsRevoked(String name) {
        var keys = apiKeyService.listAll();
        var match = keys.stream()
                .filter(k -> k.getName().equals(name))
                .findFirst()
                .orElseThrow();
        assertThat(match.isRevoked()).isTrue();
        assertThat(match.isActive()).isFalse();
    }

    @Then("the API should return not found")
    public void apiReturnsNotFound() {
        assertThat(lastApiResponse).isNotNull();
        assertThat(lastApiResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}