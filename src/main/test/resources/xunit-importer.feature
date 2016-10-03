Feature: XUnit Importer

  The preferred way to increase performance for Polarion updates of TestRun results is to use the XUnit Importer
  API.  This requires a modified xunit result file

  Scenario: Send an XUnit importer compatible XML file to xunit import API
    Given: An XML file is POST'ed to <xunit-import-url>
      """
      <testsuites>
        <properties>
            <property name="polarion-user-id" value="stoner"/>
            <property name="polarion-project-id" value="PLATTP"/>
            <property name="polarion-set-testrun-finished" value="true"/>
            <property name="polarion-dry-run" value="false"/>
            <property name="polarion-include-skipped" value="false"/>
            <property name="polarion-response-rhsm_qe" value="stoner"/>
            <property name="polarion-custom-notes" value="file:///some/otherpath"/>
            <property name="polarion-custom-jenkinsjobs" value="http://some/path"/>
            <property name="polarion-testrun-title" value="Sean Toner Polarize TestRun"/>
            <property name="polarion-testrun-id" value="Polarize TestRun"/>
        </properties>
        <testsuite name="Sanity Test" tests="2" errors="1" time="0.0" skipped="0">
            <testcase name="testUpgradeNegative" classname="com.github.redhatqe.rhsm.testpolarize.TestReq" status="success">
                <properties>
                    <property name="polarion-testcase-id" value="PLATTP-9520"/>
                    <property name="polarion-parameter-0" value="Sean"/>
                    <property name="polarion-parameter-1" value="44"/>
                </properties>
            </testcase>
            <testcase name="testUpgradeNegative" classname="com.github.redhatqe.rhsm.testpolarize.TestReq">
                <failure/>
                <properties>
                    <property name="polarion-testcase-id" value="PLATTP-9520"/>
                    <property name="polarion-parameter-0" value="Toner"/>
                    <property name="polarion-parameter-1" value="0"/>
                </properties>
            </testcase>
        </testsuite>
      </testsuites>
      """
    Then: