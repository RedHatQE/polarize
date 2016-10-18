# Why polarize?

polarize has several goals:

- Make the source code where testcase definitions and requirements live
- A TestNG reporter that generates junit reports compatible with the XUnit importer
- Creates or updates Polarion TestCase based on Java annotations during compilation
- A Java tool to send the XUnit and Testcase importer requests (no shelling out to curl)
- A Java tool to consume importer messages from the CI Message Bus (no subscribe.py)


Writing Requirements and TestCases in Polarion by hand is very time consuming.  Being able to auto-generate a Polarion 
TestCase or Requirement given metadata that already exists in an external format is more ideal. Furthermore, it is 
better to have a single source of truth (the source code) and have this reflected in Polarion, rather than try to 
maintain and keep synchronized two sets of data (annotations in the source code, and TestCase/Requirements in Polarion).

Due to performance limitations we are required to submit TestRun results via a batch operation.  This batch operation
uses a modified xunit style XML file.  Generating this xunit importer compatible file is tricky for teams using TestNG
as their framework since the junit reports it generates lacks some crucial information, like pass/fail at the testcase 
level, or parameterized arguments.  And of course, it also does not supply the modifications needed by the XUnit 
importer.

## How does it work?

Currently, the heart of polarize lies in these concepts:

1. XML description files (generated via the custom processor)
2. A custom annotation processor
3. A custom IReporter class for TestNG

In essence, polarize uses a custom annotation processor to supply metadata of your classes and test methods.  Given the
correct annotation data, when your source is compiled and the polarize jar is in your classpath, the annotation 
processor will run.  As it runs, it will find the elements which were annotated and generate XML equivalents of that 
annotation data.  In fact, these XML files are the files that are used by the CI Ops Testcase Importer tool.  These XML
files serve 2 purposes:

- Create the Polarion TestCase if the ID is empty
- A way to map the test method to the Polarion Testcase if it is not

This means that if your test ware is not annotated with the necessary metadata, then polarize is really no good.  This
is done on purpose.  The author originally wrote a tool that would auto-create TestCases in Polarion if they didn't 
already exist.  This is not only a bad idea technically, it's also wrong from a methodology perspective.  The TestCase
and Requirements really should be written before the automation is.  Disregarding the methodology, there's also a 
practical technical requirement.

One of the requirements for the XUnit importer is that each <testcase> must have a polarion ID or custom ID set.  This 
is to map the testcase to the Polarion ID.  The XML files that polarize generates will either contain the ID or not.  As
the annotation processor runs, it will use an algorithm to map the qualified name of the testmethod to where the XML 
file should be.  If it doesn't exist, the processor will create it from the given annotation data.  If the XML 
description file already exists, it will read it in to get the ID.  If the ID is an empty string, this means there is 
no Polarion TestCase for this method.  Therefore polarize will issue a Testcase importer request, and when it gets the 
response message, it will edit the XML with the proper ID (so that even though the annotation data won't have the 
Polarion ID, the generated XML will)

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
    @TestDefinition(author="stoner",               // defaults to CI User
              projectID=Project.PLATTP,            // required
              testCaseID="PLATTP-9520",            // if empty or null, make request to WorkItem Importer tool
              importance=DefTypes.Importance.HIGH, // defaults to high  if not given
              posneg=DefTypes.PosNeg.NEGATIVE,     // defaults to positive if not given
              level= DefTypes.Level.COMPONENT,     // defaults to component if not given
              // If testtype is FUNCTIONAL, subtype1 and 2 must be of type EMPTY.
              testtype=@TestType(testtype= DefTypes.TestTypes.NONFUNCTIONAL,  // Defaults to FUNCTIONAL
                                 subtype1= DefTypes.Subtypes.COMPLIANCE,      // Defaults to EMPTY (see note)
                                 subtype2= DefTypes.Subtypes.EMPTY),          // Defaults to EMPTY (see note)
              setup="Description of any preconditions that must be established for test case to run",
              teardown="The methods to clean up after a test method")
    @Test(groups={"simple"},
          description="Test for reporter code",
          dataProvider="simpleProvider")
    public void testUpgradeNegative(String name, int age) {
        AssertJUnit.assertEquals(age, 44);
        Assert.assertTrue(name.equals("Sean"));
    }
```


For testrun results, polarize also comes with a class XUnitReporter that implements TestNG's IReporter interface.  By 
setting your TestNG test to use this Reporter, it will generate the xunit which is compatible with the XUnit Importer.
In fact, if your tests use Data Providers, it will also generate the parameterized data for you too (note that this 
feature is still waiting on the ability to add TestStep and Parameters in the TestCase importer).

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
- Because workitems are external files, they can be kept in source control (single canon of truth)
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

1. src/main/resources/xml-config.xml
2. ~/.polarize/xml-config.xml

The latter (if it exists) will override the keys in the former.  It is recommended to use the latter because you will 
need to put passwords for 2 different servers in it, and you won't want to accidentally check these in. 

The config file has some documentation for what the various settings are used for and is hopefully self-documenting.

## How to use polarize

This section will describe how to make use of polarize

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
    @TestDefinition(author="stoner",               // defaults to CI User
              projectID=Project.PLATTP,            // required
              testCaseID="PLATTP-9520",            // if empty or null, make request to WorkItem Importer tool
              importance=DefTypes.Importance.HIGH, // defaults to high  if not given
              posneg=DefTypes.PosNeg.NEGATIVE,     // defaults to positive if not given
              level= DefTypes.Level.COMPONENT,     // defaults to component if not given
              // If testtype is FUNCTIONAL, subtype1 and 2 must be of type EMPTY.
              testtype=@TestType(testtype= DefTypes.TestTypes.NONFUNCTIONAL,  // Defaults to FUNCTIONAL
                                 subtype1= DefTypes.Subtypes.COMPLIANCE,      // Defaults to EMPTY (see note)
                                 subtype2= DefTypes.Subtypes.EMPTY),          // Defaults to EMPTY (see note)
              reqs={@Requirement(id="",            // if empty, look for class Requirement.  If no class requirement
                                                   // look for xmlDesc.  If none, that means request one.
                                 project=Project.RedHatEnterpriseLinux7,
                                 description="This description will override class level",
                                 xmlDesc="/tmp/path/to/xml/description/testUpgradeNegative.xml",
                                 feature="/tmp/path/to/a/gherkin/file/requirements.feature")},
              setup="Description of any preconditions that must be established for test case to run",
              teardown="The methods to clean up after a test method")
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

Given the above annotations, and xml-config.xml as shown earlier, polarize will do the following:

- Look at the xmlDesc field to get path to xml description file
  - If no such file exists, look in a configured path for the xml description file
    - If this file also does not exist, generate an xml description file from the given annotation data
      - Pass this generated XML file to the WorkItem importer for it to create a Requirement/TestCase
      - Take the return response and look for the Polarion ID.  Edit this value into the xml description file
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

**TODO** explain how to use the CIBusListener class and the JMS selector

### POST to the importers

**TODO** explain how to use the ImporterReqest class to issue the POST calls that will send to the correct endpoint

# Roadmap

Discussion on what still needs to be done.

## Feature file parser

BDD style requirement files are becoming very common, and gherkin style file the defacto standard.  If you are
unfamiliar with feature files and the gherkin syntax, please see https://cucumber.io/docs/reference

Although feature files are good at capturing a high level feature, it's setup requirements, scenario and assertions for
testing, there is still data required for Polarion that needs to be contained within the feature file (for example
if it's a positive or negative test, if it is a functional or non-functional test, or whether it's a must have, should
have etc feature).

This extra data can be contained as a parseable entity within the Feature description.  Here's an example:

```gherkin
Feature: Generate XML from annotated class

  Definition:
    severity: must_have      # One of should_have, must_have, nice_to_have, will_not_have
    type: Functional         # One of Functional, NonFunctional,
    subType:                 # optional
    status: draft            # defaults to draft
    title:                   # Not filled in by PM.  Done by QE
    plannedin                # a list of plannedin releases
      - RHEL_7_3
    links
      - /some/link/to/share  # optional

  Scenario: Annotated class generates valid XML
    Given The class is annotated with @Polarion
      And The Polarion annotation has author
      And The Polarion annotation has projectId
      And The Polarion annotation has sub-annotation Requirement
      And The field for xmlDesc is an empty string in the Annotation
     Then XML suitable for the WorkItem importer will be generated
```

## Workflow process: Feature file to Requirements

Polarize is somewhat opinionated in how it should be used.  The central concept is that when a QE needs to write
an automated test, there should already be an existing definition of the requirements.  More and more companies are
moving to BDD style tests and feature files to capture the essence of a feature and what needs to be tested.  Generally
speaking, the Product Owner, developers, and Quality Engineers should go over what needs to be done, and through this
discussion they will write a gherkin style feature file.

Once the initial feature file is hashed out between the PO, devs, and QE the written feature file can be stored in
source control within the test project.  Any subsequent changes needed can be reviewed in source control whether the
change comes due to developers gaining a better understanding of the requirement, QE's finding that there is not
enough information to be able to make assertions, or the PO getting additional feedback from customers.  This reviewal
process can be done under normal source control abilities (eg, through github PR's or gerrit for example).  Once the
review has been done the feature file will be merged in including some additional metadata.

This metadata will only be to cover information needed for a Polarion Requirement or TestCase.  By default, polarize
will look for a feature file based on the following path:

```
requirements.xml.path/<project>/<class>/<methodName>.feature
```

# Limitations

One limitation of polarize is that it assumes that there are no overloaded testmethods.  This is because polarize maps
the qualified name of a test method to a file on the system.  If you have an overloaded method (a method with the same
name but different type signature) then there will no longer be a one-to-one mapping from qualified method name to a
file.  Note that this does not apply to data driven tests.  It is perfectly fine to have a test method with data driven
parameters.

This limitation can be overcome if you specify a custom xmlDesc in the annotations.  If you do have overloaded methods, 
then you must supply a (unique) file system path.  If no file exists, polarize will generate it there.  When it needs to
get the Polarion ID, it will read in this file (which is why the path must be unique for each method).

Another limitation is that polarize has to do a lot of work to get the unique ID.  It runs a simple algorithm to map 
testmethod name to path-to-xml-file.  But then polarize has to read this xml file in, use JAXB to marshall this into a 
Testcase object, and read in the ID.  That's a lot of IO and marshalling (which can be really slow).  For teams with
thousands of tests, that might become a performance limitation.

One possible solution to mitigate this would be to write the mapping to a single file.  In essence, it would be a very
primitive key-value database.  In fact, a sophisticated solution might bundle a small database with polarize.  While
that might be overkill, it would provide some interesting querying capabilities.
