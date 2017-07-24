Feature: Configuration for a Config

  A user must be able to include data in a YAML config file, and the properties should be available to other programs.
  This yaml config file should be user specified, and must conform to a set of properties.  If the config file is non
  standard, then reading in of the config must fail, and should specify the non-conformity

  Scenario: A YAML config file is used in user specified directory
    Given the default config file exists in /tmp/default.yaml
    When a user loads the config file
    Then the config file should be loaded successfully
    And the ci.url should be 'some.server.com:12345'