# Why polarize?

polarize has several goals:

- Make the source code where testcase definitions and requirements live
- A TestNG reporter that generates junit reports compatible with the XUnit importer to update TestRun results
- Creates or updates Polarion TestCase based on Java annotations during compilation
- A Java tool to send the XUnit and Testcase importer requests (no shelling out to curl)
- A Java tool to consume importer messages from the CI Message Bus (no subscribe.py)


Writing Requirements and TestCases in Polarion by hand is very time consuming.  Being able to auto-generate a Polarion 
TestCase or Requirement given metadata that already exists in an external format is more ideal. Furthermore, it is 
better to have a single source of truth (the source code) and have this reflected in Polarion, rather than try to 
maintain and keep synchronized two sets of data and somehow link the Polarion ID to the test method.

Due to performance limitations we are required to submit TestRun results via a batch operation.  This batch operation
uses a modified xunit style XML file which gets POST'ed to a REST service endpoint on the Polarion plugin.  Generating 
this xunit importer compatible file is tricky for teams using TestNG as their framework since the junit reports it 
generates lacks some crucial information, like pass/fail at the testcase level, or parameterized arguments.  And of 
course, it also does not supply the modifications needed by the XUnit importer.

## How does it work?

Currently, the heart of polarize lies in these concepts:

1. A custom annotation processor
2. XML description files (generated via the custom processor) that describe the TestCase
3. A mapping.json file that maps class.methodName to a unique TestCase ID
4. A custom IReporter class for TestNG that generates special XUnit result files

In essence, polarize uses a custom annotation processor to supply metadata of your classes and test methods.  Given the
correct annotation data, when your source is compiled and the polarize jar is in your classpath, then the annotation 
processor will run.  As it runs, it will find the elements which were annotated and generate XML equivalents of that 
annotation data.  In fact, these XML files are the files that are used by the CI Ops Testcase Importer tool.  These XML
files serve 2 purposes:

- Create the Polarion TestCase if the ID is empty
- A (tertiary) way to map the test method to the Polarion Testcase if the ID is not in the annotation or mapping.json

The annotations exist to generate the XML equivalent description, as well as documenting metadata of the method itself.
The annotations generate XML descriptions, which are (arguably) more human readable, and can be used by upstream 
contributors to make comments on.

One of the requirements for the XUnit importer is that each \<testcase\> must have a polarion ID or custom ID set.  This 
is to map the testcase to the Polarion ID.  The XML files that polarize generates will either contain the ID or not.  As
the annotation processor runs, it will use an algorithm to map the qualified name of the testmethod to where the XML 
file should be.  The algorithm is:

1. Check the annotation testCaseID and see if it is an empty string
2. Look for the XML description file in path Project/ClassName/MethodName(.xml) and get the testcase id attribute
3. Check the mapping.json file with the key being the class.methodName of the method being processed

Once all three pieces of information are gathered, it will do the following:

| annotation  | xml      | mapping   | Action(s)                                   | Name      |
|-------------|----------|-----------|---------------------------------------------|-----------|
| 0           | 0        | 0         | Make import request                         | NONE
| 0           | 0        | 1         | Edit the XML file, add to badFunction       | MAP
| 0           | 1        | 0         | Edit the Mapping file, add to badFunction   | XML
| 0           | 1        | 1         | Verify equality, add to badFunction         | XML_MAP
| 1           | 0        | 0         | Edit the XML and mapping file               | ANN
| 1           | 0        | 1         | Verify equality, edit the XML               | ANN_MAP
| 1           | 1        | 0         | Verify equality, add to mapping             | ANN_XML
| 1           | 1        | 1         | Verify equality                             | ALL

The annotation column checks what was described in bullet 1, the xml column does what was in bullet 2, and the mapping 
column does what was in bullet number 3 above.  If the ID exists for the annotation, xml or mapping, a 1 (or true) is 
marked for that column, otherwise it is 0.  The Action(s) column describes what happens in each row.  There are eight 
rows since there are 3^2 possibilities.  In the Action(s) column, if it says "add to badFunction", this is a temporary
file that will be created in /tmp/badFunction.txt, and it will give the class.methodName of every test case it finds 
where the ID exists either in the xml or mapping.json, but the ID was not given in the annotation.  This gives the test 
author the opportunity to go back and add a newly generated Polarion ID back into the source code.

## XML Description File

Here's an example of an annotation generated XML file:

```xml
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<testcase id="PLATTP-9520" level="component" posneg="negative" importance="high" automation="automated">
    <title>com.github.redhatqe.rhsm.testpolarize.TestReq.testUpgradeNegative</title>
    <description>Test for reporter code</description>
    <nonfunctional subtype1="compliance" subtype2="-"/>
    <setup>Description of any preconditions that must be established for test case to run</setup>
    <teardown>The methods to clean up after a test method</teardown>
</testcase>
```

From the following annotation:

```java
    @TestDefinition(projectID=Project.PLATTP,      // required
                  testCaseID="PLATTP-9520",            // if empty or null, make request to WorkItem Importer tool
                  importance=DefTypes.Importance.HIGH, // defaults to high  if not given
                  posneg=PosNeg.NEGATIVE,              // defaults to positive if not given
                  level= DefTypes.Level.COMPONENT,     // defaults to component if not given
                  // linkedWorkItems is optional and allows you to specify another (existing) WorkItem.
                  linkedWorkItems={@LinkedItem(workitemId="PLATTP-10348",         // If linkedWorkItems used, required
                                   project=Project.PLATTP,                        // What Project to go under
                                   role=DefTypes.Role.VERIFIES)},                 // Role type
                  // If testtype is FUNCTIONAL, subtype1 and 2 must be of type EMPTY.
                  testtype=@TestType(testtype= DefTypes.TestTypes.NONFUNCTIONAL,  // Defaults to FUNCTIONAL
                                     subtype1= DefTypes.Subtypes.COMPLIANCE,      // Defaults to EMPTY (see note)
                                     subtype2= DefTypes.Subtypes.EMPTY),          // Defaults to EMPTY (see note)
                  setup="Description of any preconditions that must be established for test case to run",
                  tags="tier1 some_description",
                  teardown="The methods to clean up after a test method",
                  update=true,
                  automation=DefTypes.Automation.AUTOMATED)  // if not given this defaults to AUTOMATED)
    @Test(groups={"simple"},
          description="Test for reporter code",
          dataProvider="simpleProvider")
    public void testUpgradeNegative(String name, int age) {
        AssertJUnit.assertEquals(age, 44);
        Assert.assertTrue(name.equals("Sean"));
    }
```

## The mapping.json file

As well as generating the test definition XML file, at the beginning of the source code processing, polarize will load 
a mapping file in a JSON format (or if this is the first time compiling with polarize, or if the mapping file is missing 
it will generate the mapping file).  Rather than do a cumbersome look up algorithm  of qualified test name to its 
matching test definition XML file, and then loading this file to obtain the Polarion ID, the mapping file is loaded once
into memory, and used for any look up of the qualified test method name to its matching Project ID and Polarion ID.  

Note that this is a 2-level mapping of qualifiedName -\> ProjectID -\> PolarionID, since a method can and probably does 
exist in more than one Project, it will also have one or more corresponding Polarion TestCases.  The mapping file also 
contains an array of the names of the parameters used for this method if it is a parameterized test.  This is so that 
the xunit report generator can also read in this file to properly generate the parameterization information for each 
test method that was executed in the TestRun.

## Xunit report generation

Speaking of this, polarize also comes with a class XUnitReporter that implements TestNG's IReporter interface.  By 
setting your TestNG test to use this Reporter, it will generate the xunit which is compatible with the XUnit Importer.
In fact, if your tests use Data Providers, it will also generate the parameterized data for you too with the previously
mentioned mapping.json file.

After you execute your TestNG test, you will see a report generated like this:

```xml
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
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
    <testsuite name="Sanity Test" tests="2" errors="2" time="0.0" skipped="0">
        <testcase name="testUpgradeNegative" classname="com.github.redhatqe.rhsm.testpolarize.TestReq" status="success">
            <properties>
                <property name="polarion-testcase-id" value="PLATTP-9520"/>
                <property name="polarion-parameter-name" value="Sean"/>
                <property name="polarion-parameter-age" value="44"/>
            </properties>
        </testcase>
        <testcase name="testUpgradeNegative" classname="com.github.redhatqe.rhsm.testpolarize.TestReq">
            <failure/>
            <properties>
                <property name="polarion-testcase-id" value="PLATTP-9520"/>
                <property name="polarion-parameter-name" value="Toner"/>
                <property name="polarion-parameter-age" value="0"/>
            </properties>
        </testcase>
    </testsuite>
</testsuites>
```


## Why not just write requirements and testcases directly in polarion?

- Need an extra license for Product Manager to enter in information and developer to review
- Bypasses the test project as the canonical source of truth
- Couples requirement/testcase definition to a specific implementation (Polarion)
- Polarion does not allow upstream communities to review or submit requirements/workitems
- Harder to review because it's not a plain text file (no diffs)
- At the mercy of eng-ops to update/restore information about a work item

**Polarize solves all these problems**

- Because requirements/testcases are external files, dont need extra licenses to review or enter
- Because workitems are represented by external files, they can be kept in source control (single canon of truth)
- Decouples definition of requirements and testcases from a specific implementation
- Because requirements and testcases belong in source control, they can be reviewed by upstream community
- Since annotations generate plain text xml, it is easy to diff and review
- Because the xml descriptions are external, they can always be used to update existing tests on-demand

Furthermore, TestCase and Requirements can be generated automatically whenever code is compiled.  This does not mean
that the imported WorkItem is automatically approved.  It just means they will be imported into Polarion in a draft
state.

- Several sites recommend as best practice to keep requirements in source control
  - Test runners like cucumber and jbehave exist which require access to the feature file
  - http://www.testingexcellence.com/bdd-guidelines-best-practices/
  - http://sqa.stackexchange.com/questions/13780/cucumber-where-do-you-store-your-feature-files-cukes
  - https://github.com/cucumber/cucumber/wiki/Cucumber-Backgrounder
- Does not require execution of a TestRun to generate Polarion TestCase or Requirement
- Although not yet implemented, it can also be used to gather annotation metadata besides TestNG
- Since it is Java based, any test team using java can use or modify it according to their needs


# How do you use polarize?

The basic premise of polarize is that given proper annotation of the source code and a place to look for xml description
files or features files, all the mapping from a requirement/testcase to the test method can be done.

## Configuring polarize

Over time, polarize has gained a number of required settings that need to be configured.  It moved from a simple 
properties style file to the now current XML based configuration.  Work was planned on a YAML based configuration, but
although deserializing a YAML file to a POJO wasn't too hard, serializing a POJO into YAML turned out to be difficult.

There are 2 main configuration files

1. src/main/resources/polarize-config.xml
2. ~/.polarize/polarize-config.xml

The latter (if it exists) will override the keys in the former.  It is recommended to use the latter because you will 
need to put passwords for 2 different servers in it, and you won't want to accidentally check these in. 

The config file has some documentation for what the various settings are used for and is hopefully self-documenting.

## How to use polarize

This section will describe how to make use of polarize

### Example polarize-config.xml file 

```xml
<config>
  <!-- The name of your project or team.  Can be used to replace {project-name} in the 
  <testrun id="{project-name}"> attribute as a default -->
  <project-name name="RHSM-QE"/>
  <!-- The value of basedir can be used like <requirements-xml path="{basedir}/requirements"-->
  <basedir path="/home/stoner/Projects/rhsm-qe"/>
  <!-- Where Requirements XML are.  Eg <requirements-xml path="/home/stoner/Projects/rhsm-qe/requirements"/> -->
  <requirements-xml path="{basedir}/requirements"/>
  <!-- Where TestCase XML are.  Eg. <testcases-xml path="/home/stoner/Projects/rhsm-qe/testcases"/> -->
  <testcases-xml path="{basedir}/testcases"/>
  <!-- Path of the mapping.json that maps the qualified name to project -> id -->
  <mapping path="{basedir}/mapping.json"/>
  <author>ci-user</author>
  <project>RHEL6</project>
  <servers>
    <server name="polarion"
            url="https://path.to.your.polarion/polarion"
            user="foo"
            password="____________"/>
    <server name="polarion-devel"
            url="https://path.to.your.polarion-devel/polarion"
            user="foo"
            password="____________"/>
    <server name="kerberos"
            user="foo"
            password="____________"/>
    <server name="ossrh"
            user="ossrh-user"
            password="ossrh-pw"/>
    <server name="broker"
            url="tcp://your.broker:61616"/>
  </servers>
  <importers>
    <importer type="testcase"><!-- settings for the testcase importer -->
      <endpoint route="/import/testcase"/>
      <!-- The path for where the generated xml filed used for TestCase importer will be created --> 
      <file path="/tmp/testcases.xml"/>
      <!-- Creates the JMS selector
       The -->
      <selector name="rhsm_qe" val="testcase_importer"/>
      <!-- An optional prefix and suffix.  If none is given, the qualified name of the method is the title -->
      <title prefix="RHSM-TC : " suffix=""/>
      <!-- time in milliseconds to wait for message reply -->
      <timeout millis="300000"/>
      <!-- If true, when polarize needs to create a new TestCase in Polarion it will make an import request.  If false, 
        even if polarize detects that a new TestCase should be made in Polarion, it will skip making a request. The XMl 
        will still be generated, but since an import request was not made, both the annotation and XML id will be empty.
        This means once enabled is set to true, polarize will actually do the request to make a new Polarion TestCase-->
      <enabled>false</enabled>
    </importer>
    <importer type="xunit"><!-- # settings for the xunit importer -->
      <!-- id is an optional unique id for testrun. Defaults to a timestamp (uniqueness guaranteed by client)
         title is the (possibly non-unique) name of the testrun.-->
      <template-id id=""/>
      <!-- By default, this will simply be the name of the project plus a time stamp.  To make it easier to find this 
        test run in Polarion, the id should be given something more, such as a compose ID, or the version of the 
        artifact being tested.  For example: "RHEL 7.4 Server 20170314 14:57:31-->
      <testrun id="{project-name}" title=""/>
      <endpoint route="/import/xunit"/>
      <file path="{basedir}/test-output/testng-polarion.xml"/>
      <!-- # the JMS selector <name>='<value>' -->
      <selector name="rhsm_qe" val="xunit_importer"/>
      <!-- A list of key-value pairs.  The response properties are used by the xunit importer -->
      <test-suite>
          <property name="dry-run" val="false"/>
          <property name="set-testrun-finished" val="true"/>
          <property name="include-skipped" val="false"/>
      </test-suite>
      <!-- These are custom fields in the Polarion TestRun -->
      <custom-fields>
        <property name="plannedin" val=""/><!-- The plannedin phase -->
        <property name="jenkinsjobs" val=""/><!-- Path to the jenkins job -->
        <property name="notes" val=""/><!-- arbitrary field -->
      </custom-fields>
      <!-- time in milliseconds to wait for reply message -->
      <timeout millis="300000"/>
      <!-- If true, once xunit report is generated, perform an XUnit Import request to send the results to Polarion.  If
       false, do not make the XUnit Import request.  The xunit result file will still be generated however so that it 
       can be uploaded later -->
      <enabled>true</enabled>
    </importer>
  </importers>
</config>

```

### Editing the config file or a xunit result file

There is a configurator class which can be used to edit settings in the polarize-config.xml file, or in a given xunit result 
file.  The former is handy when you need to edit the polarize-config.xml file for long term changes, and the latter is nice 
to have when you only need to edit an existing xunit result file say for example to upload to Polarion.

This shows an example of modifying an existing xunit result file with other information and storing it in a new file 
/tmp/modified-testng-polarion.xml

```bash
java -cp ./polarize-0.5.5-SNAPSHOT-all.jar com.github.redhatqe.polarize.configuration.Configurator \
--current-xunit /home/stoner/Projects/rhsm-qe/test-output/testng-polarion.xml \
--new-xunit /tmp/modified-polarion.xml \
--testrun-id "Personal Testrun 1" \
--include-skipped true \
--property notes="A personal test run"
```

Here's an example of where you might want to change the polarize-config settings for a longer term purpose

```
java -cp ./polarize-0.5.5-SNAPSHOT-all.jar com.github.redhatqe.polarize.configuration.Configurator \
--edit-config \
--project RedHatEnterpriseLinux7 \
--template-id "sean toner master template test"
```

By using the --edit-config option, this will overwrite the existing polarize-config.xml file and backup the original to the 
~/.polarize/backup directory as a timestamped file.

### Annotations for Testcase definitions

The polarize project uses 2 new custom annotation types.  The @TestDefinition annotation is used for all the metadata
necessary to describe a TestCase in Polarion and is annotated on methods.

Here is a full example:

```java
package com.github.redhatqe.rhsm.testpolarize;

import com.github.redhatqe.polarize.metadata.DefTypes;
import com.github.redhatqe.polarize.metadata.Requirement;
import com.github.redhatqe.polarize.metadata.TestDefinition;
import com.github.redhatqe.polarize.metadata.TestType;
import com.github.redhatqe.polarize.metadata.DefTypes.Project;

import org.testng.Assert;
import org.testng.AssertJUnit;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * A dummy example showing how to use the annotations.  Since these are repeating annotations, that means that the
 * java source code must be compiled at Java source of 8
 *
 * Created by stoner on 7/12/16.
 */
public class TestReq {

    @TestDefinition(projectID=Project.RHEL6,
              description="TestCase for a dummy test method",
              title="A not necessarily unique title",  // defaults to class.methodName
              reqs={})
    @TestDefinition(projectID=Project.PLATTP,          // project required
                  testCaseID="PLATTP-9520",            // if empty or null, make request to WorkItem Importer tool
                  importance=DefTypes.Importance.HIGH, // defaults to high  if not given
                  posneg=PosNeg.NEGATIVE,              // defaults to positive if not given
                  level= DefTypes.Level.COMPONENT,     // defaults to component if not given
                  // linkedWorkItems is optional and allows you to specify another (existing) WorkItem.
                  linkedWorkItems={@LinkedItem(workitemId="PLATTP-10348",         // If linkedWorkItems used, required
                          project=Project.PLATTP,                                 // What Project to go under
                          role=DefTypes.Role.VERIFIES)},                          // Role type
                  // If testtype is FUNCTIONAL, subtype1 and 2 must be of type EMPTY.
                  testtype=@TestType(testtype= DefTypes.TestTypes.NONFUNCTIONAL,  // Defaults to FUNCTIONAL
                                     subtype1= DefTypes.Subtypes.COMPLIANCE,      // Defaults to EMPTY (see note)
                                     subtype2= DefTypes.Subtypes.EMPTY),          // Defaults to EMPTY (see note)
                  setup="Description of any preconditions that must be established for test case to run",
                  tags="tier1 some_description",
                  teardown="The methods to clean up after a test method",
                  // defaults to false.  If false do not make a new import even if annotation changes.  If true, the 
                  // annotation processor will _always_ make a new import request even if nothing changes
                  update=false,  
                  automation=DefTypes.Automation.AUTOMATED)  // if not given this defaults to AUTOMATED)
    @Test(groups={"simple"},
          description="Test for reporter code",
          dataProvider="simpleProvider")
    public void testUpgradeNegative(String name, int age) {
        AssertJUnit.assertEquals(age, 44);
        Assert.assertTrue(name.equals("Sean"));
    }

    @DataProvider(name="simpleProvider")
    public Object[][] dataDriver() {
        Object[][] table = new Object[2][2];
        List<Object> row = new ArrayList<>();

        row.add("Sean");
        row.add(44);
        table[0] = row.toArray();

        row = new ArrayList<>();
        row.add("Toner");
        row.add(0);
        table[1] = row.toArray();
        return table;
    }
}
```

Notice that the above uses repeating annotations, a feature only available in Java 8.  If your test method differs 
in some aspect of the TestCase definition within Polarion, then you can enter 2 or more annotations.  For example, if 
the description, setup, teardown, or any other information differs between RHEL6 and RedHatEnterpriseLinux7 for your 
test method, then you should annotate it twice as above.  If your definitions are otherwise identical, you can just 
put the projectID={Project.RHEL6, Project.RedHatEnterpriseLinux7} for example.

Given the above annotations, and polarize-config.xml as shown earlier, polarize will do the following:

- Look at the xmlDesc field to get path to xml description file
  - If no such file exists, look in a configured path for the xml description file
    - If this file also does not exist, generate an xml description file from the given annotation data
      - Pass this generated XML file to the WorkItem importer for it to create a Requirement/TestCase
      - Take the return response and look for the Polarion ID.  Edit this value into the xml description file
      - From the information in the XML and annotation, edit the mapping.json file 
- Once the xml file is ready, we know the mapping between test method and Polarion Requirement and TestCase ID
- When the test run is done and the junit report is created, post process the result file

Here are some more examples.

**Simplified (using defaults)**

```java
    /**
     * Shows an example of TestSteps with default named Params.
     *
     * This is a test that uses a DataProvider.  The annotation processor will determine the names of the arguments
     * and use this as the names for the TestStep Parameters.  In the example below, the processor will determine that
     * this method has 2 arguments called "name" and "age".  It will create the XML necessary to include those params.
     * 
     * Notice that this also has projectID set to an array.  If the TestDefinition is identical except for the project
     * then you can set the projectID like this.  If there is any difference, then you must use a repeating annotation.
     * Note also that if the projectID is an array, that the testCaseID must 
     *
     * @param name
     * @param age
     */
    @TestDefinition(author="stoner", projectID={Project.RHEL6, Project.RedHatEnterpriseLinux7})
    @Test(groups={"simple"},
          description="Shows how to add parameters named '0', '1', '2', etc through @TestSteps",
          dataProvider="simpleProvider")
    public void testUpgrade(String name, int age) {
        Assert.assertTrue(name.equals("Sean") || name.equals("Toner"));
        Assert.assertTrue(age < 100);
    }

    @DataProvider(name="simpleProvider")
    public Object[][] dataDriver() {
        Object[][] table = new Object[2][2];
        List<Object> row = new ArrayList<>();

        row.add("Sean");
        row.add(44);
        table[0] = row.toArray();

        row = new ArrayList<>();
        row.add("Toner");
        row.add(0);
        table[1] = row.toArray();
        return table;
    }
```

### Making use of XUnitReporter for TestNG

Here's a little snippet of how to setup your test for debugging

```bash
gradle clean
gradle pP
gradle publishToMavenLocal
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5006 \
-cp /path/to/your/polarize-1.0.0-all.jar:/path/to/your/awesome-test-1.0.0.jar \
org.testng.TestNG -reporter com.github.redhatqe.polarize.junitreporter.XUnitReporter /path/to/your/test-suite.xml
```

Basically, you just need to put polarize on your classpath, and add the -reporter (or in your XML suite file add the 
XUnitReporter under <listeners>).  TestNg will take care of everything else and will generate your xunit file usable 
but the XUnit importer.

### Listening to CI Bus messages

Because the importers (XUnit and TestCase) send replies back as a message on the CI Bus, it is necessary to listen 
for these messages.  The polarize-config.xml file contains entries like this:

```xml
    <importer type="testcase">
      <!-- ellided entries  -->
      <selector name="rhsm_qe" val="testcase_importer"/><!-- Creates the JMS selector -->
      <!-- ellided entries -->
    </importer>
```

The \<selector\> element contains 2 attributes named name and val.  The JMS selector used to filter out all the messages
on the bus uses the format "{name}='{val}'".  There are selector elements for both the testcase and xunit importers.

For command line usage of the XUnit Importer, one can override the config file settings by using the --selector option.
It should take an argument like --selector "name='val'".  For example:

```bash
java -cp ./polarize-0.5.4-all.jar com.github.redhatqe.polarize.junitreporter.XUnitReporter --selector "rhsm_qe='my_tag'"
```

#### Embedding in a java project

**TODO** Show how to use the CIMessageBusListener in a java project and as a standalone tool

#### Message Status

In the case of the XUnitReporter, the JSON message will look like this on success:

```json
{
  "testrun-url" : "https://mypolarion.server.com/polarion/#/project/RHEL6/testrun?id=RHEL6%202016-11-04%2001-31-39-498",
  "import-results" : [ {
    "suite-name" : "CLI: BashCompletion Tests",
    "status" : "passed"
  } ],
  "status" : "passed",
  "log-url" : "http://my.logstash.server/polarion/RHEL6/20161104-013138.523.log"
}
```

### POST to the importers

polarize can be used as a standalone tool to make an importer request, or it can be embedded in another java project.  
There is no curl call for the POST'ing of the XML to the endpoint.  Here's an example of manually sending an XUnit 
request using the XUnitReporter class

**TODO** Give examples of how to run the XUnit and TestCase importers as a standalone tool, and how to embed in another 
java project

# Using polarize with clojure or other dynamic JVM languages

Since polarize's main use was for Java annotations that could be processed at compile time, this doesn't make sense 
for clojure.  When you run lein compile, even though polarize is in the classpath, it does not run the annotation 
processor.  In theory, this should work, since annotation processors can examine either java source code or bytecode 
(and lein compile generates .class files), but I was unable to get leiningen to "hook" into our clojure code.  I even 
tried modifying the testng-clj project's gen-class-testng macro, since it is the macro that runs gen-class on all the 
clojure code to generate the java classes, and I told it to also accept any method annotated with a TestDefinition 
annotation.  But even that did not cause the TestDefinitionProcessor class to be executed on the bytecode.

So an alternative solution was to grab the annotation information at runtime.  All annotations have a retention policy.
An annotation can only exist at compile time, or it can exist at runtime.  The latter is useful if your code uses 
reflection so that you can use annotations as metadata.  To use the annotation at runtime, you need to compile your 
project code as an uberjar (aka fatjar).  You then need to put this uberjar on the classpath with polarize, and also 
add it as a command line option.  Since polarize uses gradle as its build tool, it's a little tricky to get it to 
return the classpath (ie, the equivalent of `lein classpath`).  There is a task I created called classPath which will
display the classpath, but also some extraneous information.  I just copy what I need and export it into a var:

Once you have saved off the classpath to a var (say $CP), you can run the annotation processing like this:

```
cd /path/to/rhsm-qe
export CP=`lein classpath`
export CP="$CP:${HOME}/.m2/repository/com/github/redhatqe/polarize/polarize/0.5.5-SNAPSHOT/polarize-0.5.5-SNAPSHOT-all.jar
lein clean
lein uberjar
java -cp $CP -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5009 com.github.redhatqe.polarize.JarHelper \
--jar file:///home/stoner/Projects/rhsm-qe/target/sm-1.1.0-SNAPSHOT-standalone.jar \
--packages "rhsm.gui.tests,rhsm.cli.tests"
```

The --jar option specifies the path of the uberjar, and the --packages is a comma separated list of packages to scan 
for.  Note that this example also sets the debugger option so that you can setup a remote debug configuration if needed.

## Debugging 

Speaking of debugging, if you need to debug your clojure code you can set in your project.clj file the following:

```clojure
:jvm-opts ["-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5007"]
```

In your Intellij editor, you can set up a remote debug profile and have it listen to port 5007.  Once the IDE connects, 
the rhsm-qe tests will run, and will stop once it hits any break points you set in your IDE

# Roadmap

See the ROADMAP.md file

# Limitations and known TODOs

Here is a compilation of known limitations and TODO's.  Keep these in mind when using polarize

## Editing the polarize-config.xsd

If you ever need to edit the polarize-config.xsd file to add new configuration properties, you will need to cd into the 
src/main/resources/configuration directory, and then run the configuration.sh script.  Due to some weirdness with JAXB 
the generated class for the ConfigType does not get annotated with @XmlRootElement(name="config").  So remember to 
manually add this to the ConfigType class.

There appears to be some workarounds by using the ObjectFactory to marshall/unmarshall classes that are not annotated 
with the XmlRootElement, but I have not looked into that method.

## Unique method names

One limitation of polarize is that it assumes that there are no overloaded testmethods.  This is because polarize maps
the qualified name of a test method to a file on the system.  If you have an overloaded method (a method with the same
name but different type signature) then there will no longer be a one-to-one mapping from qualified method name to a
file.  Note that this does not apply to data driven tests.  It is perfectly fine to have a test method with data driven
parameters.

## Many methods to one testcase

The XUnit Importer has the ability to do different lookups.  There are 3 possibilities:

- id (default): Uses the Polarion TestCase ID
- custom: Allows the user to have a custom (but unique) ID of method -> customID
- name: If the class.name is unique, then this can be used

The last lookup by name was added after polarize had already (mostly) been written.  However, the name lookup also will 
not work where you have several test methods which verify the same TestCase.  For example if you have more than one way
of doing essentially the same test.  Some might argue that there should be a one-to-one mapping, but TestCases should 
be somewhat implementation agnostic.

## Setting your own title and xml path

The following annotation fields do not yet currently work:

- title
- xmlDesc

These fields were included as a possible workaround (that still needs to be fully implemented ) for the limitation of 
a unique method name.  By specifying a custom title and xmlDesc in the annotations, polarize would use those as the 
means to map the test method to the Polarion ID.  If you do have overloaded methods, then you must supply a (unique) 
file system path.  If no file exists, polarize will generate it there.  When it needs to get the Polarion ID, it will 
read in this file (which is why the path must be unique for each method).  This is the reason that the title and xmlDesc
do not yet work, because some additional work still needs to be put into place.

## Fragility of mismatched testcase IDs

The system is also somewhat fragile in the sense if the mapping.json file ever gets edited or lost.  This can be 
mitigated somewhat in the future by allowing a regeneration of the mapping.json file by looking through all the XML 
description files.  It's also somewhat mitigated due to this file being checked into git.

Another quirk is that there are 8 possible states to check for the existence of a testcase ID:

| annotation  | xml      | mapping   | Action(s)                                   | Name      |
|:-----------:|:--------:|:---------:|---------------------------------------------|-----------|
| 0           | 0        | 0         | Make import request                         | NONE
| 0           | 0        | 1         | Edit the XML file, add to badFunction       | MAP
| 0           | 1        | 0         | Edit the Mapping file, add to badFunction   | XML
| 0           | 1        | 1         | Verify equality, add to badFunction         | XML_MAP
| 1           | 0        | 0         | Edit the XML and mapping file               | ANN
| 1           | 0        | 1         | Verify equality, edit the XML               | ANN_MAP
| 1           | 1        | 0         | Verify equality, add to mapping             | ANN_XML
| 1           | 1        | 1         | Verify equality                             | ALL

Notice in the table above under the Action(s) column something that says "add to badFunction".  This means that the ID 
existed in either the XML file or the mapping file, but not in the annotation.  Ideally, this information should always
be put into the source annotation, but it is not possible to rewrite code in an annotation process (you can generate new 
code based on an annotation, but you can't edit existing code).  

To help ease the problem where the ID might exist in the XML or mapping file, but not in the annotation, everytime the 
code is compiled or the JarHelper main is called, a file /tmp/bad-functions.txt is created which will list the bad 
functions like this:

```
~/P/testpolarize ❯❯❯ cat /tmp/bad-functions.txt                                                                                                           master ✱
For com.github.redhatqe.rhsm.testpolarize.TestReq.testBadProjectToTestCaseID, in project PLATTP, the testCaseID is an empty string even though the corresponding XML file is present and has ID = PLATTP-10202
For com.github.redhatqe.rhsm.testpolarize.TestReq.testUpgrade, in project PLATTP, the testCaseID is an empty string even though the corresponding XML file is present and has ID = PLATTP-10068
For com.github.redhatqe.rhsm.testpolarize.TestReq.testError, in project PLATTP, the testCaseID is an empty string even though the corresponding XML file is present and has ID = PLATTP-10203
For com.github.redhatqe.rhsm.testpolarize.TestPolarize.testMethod, in project PLATTP, the testCaseID is an empty string even though the corresponding XML file is present and has ID = PLATTP-10069
```

## What to do if there is a mismatch?

Also, there's the thorny problem of what to do if the testcase ID's don't match in one or more the above entities.  For 
example, what if someone accidentally edits an annotation so that it no longer matches what is in the XML or in the 
mapping file?  One of the entities should be the authoritative source, but which one?  An argument could be made for 
why any of the 3 should be the authoritative version.  However, polarize has made the choice to make the annotation as 
the authoritative source.

But even then, what to do if there is a mismatch?  If the annotations says a testmethod maps to RHEL6-23456, but the 
(matching) XML definition for that method maps to RHEL6-23457, what to do?  Currently, there is not a query mechanism 
in place, so we can not yet query the two ID's and see which one to pick.  In this case, automation can not and should
not automatically resolve these conflicts anymore than git merge can automatically resolve source conflicts.  A human 
must intervene.  However, polarize will at least fail on the compile if there is a mismatch on the "verify equality"
check so that this intervention can take place.

## Setting the update field

Over time, you might want to update one or more fields of the annotation to update the matching TestCase definition in 
Polarion.  You can set a field called update=true and edit any of the fields to do so.  The problem is that if you later
forget to unset the update field, every time the code is compiled, it will make a new import request.  This is a waste 
of resources and a burden on the polarion server.

# Building polarize

Building polarize is relatively straight forward and uses gradle.  The tricky part is that polarize is meant to be 
uploaded to maven central so that other teams may make use of it.

Firstly, you should have a ~/.polarize/polarize-properties.properties file that contains two things:

```
ossrhUsername=
ossrhPassword=
```

These should be the username and password to your nexus sonatype repository manager.

The project also makes use of SNAPSHOT repositories.  If the version contains the word SNAPSHOT in it in the build file, 
then when you publish the polarize artifacts, it will go into maven's snapshot repository.  If your project uses maven, 
gradle or leiningen, you may need to add the snapshot repository to your project.

When building a new version of polarize, all you need to do is edit the version variable in the build.gradle file.  Then 
you can run the following sequence of commands from your polarize directory

```
gradle clean
gradle build
gradle install
```

The gradle clean command will remove the temporary build directory.  The gradle build command will build a maven pom 
file, and store that in a local artifacts directory.  

## Uploading to maven central

When you are ready to make a release version (ie, a version that doesn't have SNAPSHOT in the version name) you will 
need to have a few things set up.  In your ~/.gradle/gradle.properties file, include the following:

- signing.keyId= id of your pgp key
- signing.password= the password you used for your pgp key
- signing.secretKeyRingFile=/home/stoner/.gnupg/secring.gpg

Gradle uses this information to know how to sign all the artifacts.  This is a requirement for maven central, so you 
must have a pgp/gpg key for the signing, and the oss central site must have your public key registered to a public site.
For more information on getting set up with Sonatype, please read their page:

http://central.sonatype.org/pages/ossrh-guide.html

## Logging into Nexus

Log into https://oss.sonatype.org#welcome

Enter with your username and password.  Click on the [Staging Repositories][-nexus] link.  Scroll down until you see 
your project.  Click on Close to close the stage if all the files look good, otherwise edit, and run gradle publish 
again.

When you close, give it a minute or so and then click the refresh button.  If everything went well, you should see a 
Release button.  Click that to release.

It takes several minutes or even hours for the oss repo to mirror to maven central.  You can add this repo.

If you are using leiningen for your builds, you can add the maven central repo like this:

```clojure
:repositories [["clojars.org" {:url       "http://clojars.org/repo"
                                 :snapshots {:update :always}}]
               ["sonatype"    {:url        "http://oss.sonatype.org/content/repositories/releases"
                              ;; If a repository contains releases only setting
                              ;; :snapshots to false will speed up dependencies.
                              :snapshots     false
                              ;; Disable signing releases deployed to this repo.
                              ;; (Not recommended.)
                              :sign-releases true
                              ;; You can also set the policies for how to handle
                              ;; :checksum failures to :fail, :warn, or :ignore.
                              :checksum      :fail
                              ;; How often should this repository be checked for
                              ;; snapshot updates? (:daily, :always, or :never)
                              :update        :always}]
                ["sonatype-snaps"
                             {:url     "https://oss.sonatype.org/content/repositories/snapshots"
                              ;; If a repository contains releases only setting
                              ;; :snapshots to false will speed up dependencies.
                              :snapshots     true
                              ;; Disable signing releases deployed to this repo.
                              ;; (Not recommended.)
                              :sign-releases false
                              ;; You can also set the policies for how to handle
                              ;; :checksum failures to :fail, :warn, or :ignore.
                              :checksum      :warn
                              ;; How often should this repository be checked for
                              ;; snapshot updates? (:daily, :always, or :never)
                              :update        :always}]
                 ]
```

## Forgotten password

In the event that you forgot your username or password for nexus, you can go to their [Jira site][-nexus-pw]

# Other metadata mechanisms for other languages

The idea of using a language's built in feature for metadata is desirable.  One could try to write a language agnostic 
way to capture metadata, for example reading python's docstings or java's javadocs, but you still have a language 
specific feature (the parser would have have to recognize what a docstring was for a python method, or a javadoc).  Also 
comments are usually meant for that, comments, not metadata.  Java annotations are actually exactly the use case for 
adding metadata about a test.  If I am a user of a library, I do not want to read in my javadocs or sphinx generated 
docs about some Test Case Repository system...I want to know what the functions or classes do. 

So while polarize follows this idea, the majority of test teams use either python or bash, and not a few use ruby.  
Other teams might be using javascript or some other language.  So then, how do you create metadata for these other 
languages?

## Target representation 

As of now, polarize generates XML based on the annotation data.  This is because the TestCase Importer made by the CI 
Ops team expects an XML file in order to generate the Polarion TestCase Workitem.  In other words, XML is just a middle 
man.  It should be possible to store the metadata in other forms like json

## Python 

Probably the best way to do this is to create decorators which in turn generate XML.  Ironically, the syntax for python 
decorators could look a lot like the java version.  For example, imagine something like this:

```python
from functools import wraps

base_path = get_base_from_config()
mapping_path = get_mapping_from_config()

def test_definition(**kwargs):
    kwargs["project"] = kwargs.get("project", "RHEL6")
    kwargs["test_case_id"] = kwargs.get("test_case_id")
    kwargs["importance"] = kwargs.get("importance", "MEDIUM")
    kwargs["posneg"] = kwargs.get("posneg", "POSITIVE") 
    kwargs["level"] = kwargs.get("level", "SYSTEM")
    kwargs["linked_work_items"] = kwargs.get("linked_work_items")
    kwargs["testtype"] = kwargs.get("testtype")
    kwargs["setup"] = kwargs.get("setup", "")
    kwargs["teardown"] = kwargs.get("teardown", "")
    kwargs["tags"] = kwargs.get("tags")
    kwargs["update"] = kwargs.get("update", False)
    kwargs["automation"] = kwargs.get("automation")

    # Set up the defaults 
    test_case_id = kwargs["test_case_id"]
    update = kwargs["update"]
    if kwargs["testtype"] is None:
        # set the default
        pass

    def wrapper(fn):
        # Check to see if the XML description file for this method exists
        # If it doesn't or update == true, regenerate the XML
        # For performance, you can have a global setting to always disable this if desired
        xml_path = get_xml_path(fn.__name__, base_path)
        if update or does_xml_exist(xml_path):
            generate_xml(kwargs)
            
        # Check the test_case_id, xml file, and mapping.json.  Do what needs to be done based on the 8 possibilities
        do_import = get_test_case_id_from_all(xml_path, mapping_path, test_case_id)
        if do_import:
            test_case_id = import_request(xml_path)
            # edit the XML and mapping.json with the new test_case_id
            edit_xml(xml_path, test_case_id)
            edit_mapping(mapping_path, test_case_id)
            

        @wraps
        def processor(*args, **kwds):
            # Actually run our test method
            return fn(*args, **kwds)
        return processor
    return wrapper


@test_definition(project="RedHatEnterpriseLinux7",
                 test_case_id="",
                 importance="HIGH",
                 posneg="NEGATIVE")
def my_test_method():
    pass
```

## Javascript 

Javascript doesn't have syntactic sugar like python does for decorators, but all python decorators are, is a syntax 
convenience which is a higher order function that takes another function (and some additional parameters) and returns 
a modified function.

```javascript
const test_definition = (test_def, wrap_fn) => {
    // 1. Set defaults for test_def 
    // 2. 
    return wrap_fn;
}

function my_test_method() {
    // do your test
}

var defs = {"project": "RHEL6", 
            "importance": "HIGH", 
            "level": "COMPONENT"}
const my_test_method = test_definition(defs, my_test_method)
```

## Ruby 

Unfortunately, I don't know ruby so I can't give an example, however, since ruby has the concept of higher order 
functions, it can follow the same strategy as javascript

[-nexus-pw]: https://issues.sonatype.org/secure/ForgotLoginDetails.jspa
[-nexus]: https://oss.sonatype.org/#stagingRepositories