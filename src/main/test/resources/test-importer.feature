Feature: Testcase importer request

  Scenario: Send an XML file to testcase import endpoint in polarion-devel
    Given:  An XML file to create a new testcase without an attribute of id being set
      """
      <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
      <testcases project-id="PLATTP" user-id="stoner">
          <testcase level="component" posneg="positive" importance="high" automation="automated">
              <title>com.github.redhatqe.rhsm.testpolarize.TestPolarize.testMethod2</title>
              <description>Another simple test for testpolarize</description>
              <functional subtype1="-" subtype2="-"/>
          </testcase>
      </testcases>
      """
    When: the XML file is POST'ed to <testcase-import-url>
    Then: a message with the new Polarion ID is sent to the CI Message Bus
    And: a new testcase with this ID is created in polarion