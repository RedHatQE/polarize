# Roadmap

Discussion on improvements to polarize

Right now, polarize has a couple of drawbacks:

- Not very user-friendly
- Not modular (eg someone only wants to listen for Message Bus requests)
- Still have to do a lot of work for legacy tests
- No requirements importer

## Modularity

The first task will be to break up polarize into some separate components.  The components will be:

- message bus library
- configuration and helper library
- report generation library

This way, if a user just wants to generate the xunit file, or they just want to receive messages the response message
back from the CI Bus, they can only add the dependency they need.  Also, if they only need to listen for CI messages, 
the configuration file they have to use will be much smaller.  

Part of the complexity of polarize is knowing what parts of the configuration you need to tweak.  Also, the 
configuration code is probably the most needlessly complex and spaghetti like code in polarize.  So the configuration 
code will be cleaned up to make it easier for people to use.  For example, instead of XML, it will be YAML based.  Also
the ability to add and debug all the different config options needs to be easier.  Right now, config settings can be 
done from a config file, the environment, and CLI options.

polarize 1.0 will utilize the new modularized components.  It will have backwards compatibility with pre 1.0 for the 
config options, including the CLI.  From an end-user perspective, other than the config being in YAML, nothing should
change.

## Polarize microservices

Right now, polarize is not very user-friendly and it's hard to even explain.  So, let's make it a service instead.  The
annotations will still be required, but make configuration easier.

### Xunit Import service

The reason for this is two-fold.  On one hand, we need it to make it easier.  Secondly, it wont require a jenkins job
to make a TestRun.  Thirdly, because the Polarion plugin can take a very long time, we need a way to not tie down an 
executor on a jenkins node to know if/when the TestRun is created.

### mapping.json service

A lot of the hard work for a team is doing the mapping of method name to Polarion (or custom) ID.  For teams that have
a lot of TestCases in Polarion already, it will be required to have a way to 

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
    Given The class is annotated with @TestDefinition
      And The TestDefinition annotation has author
      And The TestDefinition annotation has projectId
      And The TestDefinition annotation has sub-annotation Requirement
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