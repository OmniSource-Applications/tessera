Feature: Workspace Management
  As a Tessera administrator
  I want to create, list, and delete workspaces
  So that I can organize my geospatial data sources

  Scenario: Create a new workspace
    Given I am authenticated as "admin"
    When I create a workspace named "bdd-workspace"
    Then the workspace "bdd-workspace" should exist
    And the workspace list should contain "bdd-workspace"

  Scenario: Prevent duplicate workspace creation
    Given I am authenticated as "admin"
    And a workspace named "dup-workspace" exists
    When I try to create a workspace named "dup-workspace"
    Then I should receive an error about the workspace already existing

  Scenario: Delete a workspace
    Given I am authenticated as "admin"
    And a workspace named "to-delete" exists
    When I delete the workspace "to-delete"
    Then the workspace "to-delete" should not exist

  Scenario: Reject invalid workspace name
    Given I am authenticated as "admin"
    When I try to create a workspace named "has spaces!"
    Then I should receive a validation error