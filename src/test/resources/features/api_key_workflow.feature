Feature: API Key Workflow
  As a Tessera administrator
  I want to manage API keys for programmatic access
  So that external applications can securely query my data

  Scenario: Enable API keys and generate a key
    Given I am authenticated as "admin"
    And the API key feature is enabled
    When I create an API key named "ci-pipeline" with scopes "QUERY_READ,QUERY_EXECUTE"
    Then I should receive a raw key starting with "tsk_"
    And the key "ci-pipeline" should appear in the key list

  Scenario: Authenticate with a valid API key
    Given I am authenticated as "admin"
    And the API key feature is enabled
    And I have created an API key named "auth-test"
    When I call the query API with the generated key
    Then the API request should succeed

  Scenario: Revoke an API key
    Given I am authenticated as "admin"
    And the API key feature is enabled
    And I have created an API key named "revoke-test"
    When I revoke the key "revoke-test"
    Then the key "revoke-test" should be marked as revoked

  Scenario: Disabled feature rejects key creation
    Given I am authenticated as "admin"
    And the API key feature is disabled
    When I try to create an API key via the API
    Then the API should return not found