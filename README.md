Requirements and TestCases in Polarion by hand is just too time consuming.  Being able to auto-generate a Polarion 
TestCase or Requirement given metadata that already exists in the source code is more ideal. Furthermore, it is better 
to have a single source of truth (the source code) and have this reflected in Polarion, rather than try to maintain and
keep synchronized two sets of data (annotations in the source code, and TestCase/Requirements in Polarion).


## Why not just write requirements and testcases directly in polarion?

- Need an extra license for Product Manager to enter in information
- Need extra licenses for Developers to review in Polarion
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

polarize is fairly easy to configure.  It really just needs to know where to generate/look for the xml description files
that are analogous to the annotation data.  polarize will look in 2 places for configuration data:

1. src/main/resources/polarize.properties
2. ~/.polarize/polarize.properties

The latter (if it exists) will override the keys in the former.  The two keys are:

requirements.xml.path=/home/stoner/Projects/testjong/requirements
testcase.xml.path=/home/stoner/Projects/testjong/testcases

requirements.xml.path is a base path to where to generate or look for xml files to create Requirements in Polarion.
testcase.xml.path is a base path to where to generate or look for xml files to create TestCases in Polarion.

## How to use polarize

The polarize project uses 2 new custom annotation types.  The @Polarion annotation is used to enter all the metadata
necessary to describe a TestCase in Polarion and is annotated on methods, and the @Requirement annotation contains all
the metadata necessary to describe a Requirement WorkItem type in Polarion.  The @Requirement annotation can be used
either on a class or a method (if used on a method, it will override its parent class annotation if there is one).

Here is an example:

```java
package com.redhat.qe.rhsm.testjong;

import com.redhat.qe.rhsm.metadata.TestDefinition;
import com.redhat.qe.rhsm.metadata.Requirement;
import org.testng.annotations.Test;

/**
 * A dummy example showing how to use the annotations.  Since these are repeating annotations, that means that the
 * java source code must be compiled at Java source of 8
 *
 * Created by stoner on 7/12/16.
 */
@Requirement(project="RHEL6", author="Sean Toner",
             description="Class level Requirement for RHEL 6.  All test methods will inherit from a class annotated " +
                     "with @Requirement.  If a test method's @Polarion annotation has a non-empty reqs field, any " +
                     "@Requirements there will override the class Requirement for that method")
@Requirement(project="RedHatEnterpriseLinux7", author="CI User",
             description="Class level Requirement for RHEL7")
public class TestJongReq {

    @TestCase(projectID="RHEL6",
              description="TestCase for a dummy test method",
              reqs={})
    @TestCase(author="Sean Toner",                 // required
              projectID="RedHatEnterpriseLinux7",  // required
              testCaseID="RHEL7-56743",            // if empty or null, make request to WorkItem Importer tool
              caseimportance="medium",             // defaults to high  if not given
              caseposneg="negative",               // defaults to positive if not given
              caselevel="component",               // defaults to component if not given
              testtype="non_functional",           // defaults to functional if not given
              reqa={@Requirement(id="",            // if empty, look for class Requirement.  If no class requirement
                                                   // look for xmlDesc.  If none, that means request one.
                                 xmlDesc="/path/to/xml/description",
                                 feature="/path/to/a/gherkin/feature/file")},
              setup="Description of any preconditions that must be established for test case to run",
              teardown="The methods to clean up after a test method")
    @Test(groups={"testjong_polarize"},
          description="A simple test for polarize")
    public void testUpgradeNegative() {
         System.out.println("Dummy negative test");
    }
}
```

Given the above annotations, and polarize.properties as shown earlier, polarize will do the following:

- Look at the xmlDesc field to get path to xml description file
  - If no such file exists, look in a configured path for the xml description file
    - If this file also does not exist, generate an xml description file from the given annotation data
      - Pass this generated XML file to the WorkItem importer for it to create a Requirement/TestCase
      - Take the return response and look for the Polarion ID.  Edit this value into the xml description file
- Once the xml file is ready, we know the mapping between test method and Polarion Requirement and TestCase ID
- When the test run is done and the junit report is created, post process the result file


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

# How does polarize work

Currently, the heart of polarize lies in 2 concepts:

1. XML description files (generated via the custom processor)
2. A custom annotation processor

The XML description file is what is used to do the mapping from a method name to a unique Polarion ID.
If no XML description file exists (in the path determined by configuration, work item type, class and method name) then
the annotation processor will use the information in the annotation to create an XML description file.  If a description
file needs to be created, the annotation processor will also make a request using the WorkItem importer to generate the
Requirement and/or TestCase in Polarion.

