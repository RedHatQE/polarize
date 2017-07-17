Feature: Configuration capabilities

  Configuration can be done from a combination of a JSON file, a database graph, Environment variables, or CLI options.
  Since these can all interact with each other, this is always a very challenging feature to implement and test.

  Scenario: Read a JSON config file
    Given the file named "busconfig.json"
    And it is located in "HOME"
    And it has the contents of
      """
      {
      }
      """