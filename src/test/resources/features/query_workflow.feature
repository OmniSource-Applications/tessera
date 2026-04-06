Feature: Query Catalog Workflow
  As a Tessera user
  I want to create, execute, and manage catalog queries
  So that I can analyze my geospatial data

  Scenario: Create and execute a custom query
    Given I am authenticated as "admin"
    When I create a catalog query "bdd.test_query" with SQL "SELECT 42 AS answer"
    Then the query "bdd.test_query" should exist in the catalog
    When I execute the query "bdd.test_query"
    Then the query execution should succeed
    And the result should contain 1 row

  Scenario: Execute a parameterized query
    Given I am authenticated as "admin"
    And I create a catalog query "bdd.param_query" with SQL "SELECT :val::int AS result" and params:
      | name | type    | default |
      | val  | integer |         |
    When I execute the query "bdd.param_query" with parameters:
      | val | 99 |
    Then the query execution should succeed
    And the result should contain a row where "result" equals 99

  Scenario: Reject query with write operations
    Given I am authenticated as "admin"
    When I try to create a catalog query "bdd.bad_write" with SQL "INSERT INTO foo VALUES (1)"
    Then I should receive a validation error about read-only queries

  Scenario: Delete a catalog query
    Given I am authenticated as "admin"
    And I create a catalog query "bdd.to_delete" with SQL "SELECT 1"
    When I delete the query "bdd.to_delete"
    Then the query "bdd.to_delete" should not exist in the catalog