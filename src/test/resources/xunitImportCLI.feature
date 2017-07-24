Feature: Use the XUnitReporter CLI to generate a new xunit based on an old one

  The CLI tool helps the user take an existing xunit XML file, and create a _new_ modified one based on it.  It does
  not edit the existing file in place.

  Scenario Outline: Generate a new xunit based on an old one
    Given the default xunit file
      """

      """
    And the default config file is used
    When a CLI option of <option> <value> is passed to the CLI
    Then a new xunit file in /tmp/modified-polarize.xml should exist
    And the resulting xunit should have a corresponding element of <expected>
    Examples:
      | option                | value                  | expected                                       |
      | --testrun-title       | "just a test"          | "polarion-testrun-title","just a test"         |
      | --project             | RedHatEnterpriseLinux7 | "polarion-project-id","RHEL6"                  |
      | --testrun-type        | buildacceptance        | "polarion-testrun-type-id","buildacceptance"   |
      | --testrun-template-id | "test template"        | "polarion-testrun-template-id","test-template" |
      | --property            | arch=aarch64           | "polarion-custom-arch","aarch64"               |
      | --xunit-selector-name | stoner                 | "polarion-response-stoner", "xunit_importer"   |

