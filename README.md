# jong

Jong is a tool that uses Java runtime reflection to get annotation data about classes and methods.  Specifically, it looks for
TestNG annotations so that @Test annotated classes and methods can be gathered.  However, it is not hard to extend what kinds of
annotations to look for, so that junit or other test frameworks metadata can be gathered.

## Rationale

Many teams already have hundreds, if not thousands, of legacy tests in their test suites.  Asking them to generate Requirements 
and TestCases in Polarion by hand is not only silly, it's just too time consuming.  Being able to auto-generate a Polarion Test
Case or Requirement given metadata that already exists in the source code is more ideal.  Furthermore, it is better to have a 
single source of truth (the source code) and have this reflected in Polarion, rather than try to maintain and keep synchronized
two sets of data (annotations in the source code, and TestCase/Requirements in Polarion).

## Advantages

Currently, the rhsm-qe team uses the pong project to upload TestRun results to Polarion.  As pong parses a testng-results.xml file
it also checks to see if there is a matching TestCase and Requirement for an executed <test-method> element.  While this works, it
means that TestCase and Requirements are only generated if the test was actually in that run.  So jong provides these benefits

- Does not require execution of a TestRun to generate Polarion TestCase or Requirement
- Although not yet implemented, it can also be used to gather annotation metadata besides TestNG
  - pong relies on parsing testng-results.xml and this part of the code is very complex
- Since it is Java based, any test team using java can use or modify it according to their needs
