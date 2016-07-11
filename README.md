# polarize

polarize is a tool that uses Java runtime reflection and an Annotation Processor at compile time to get annotation data
about classes and methods.  Specifically, it does two things:

1) @Test TestNG annotations so that @Test annotated classes and methods can be gathered.  
2) @Polarion and @Requirement annotations to gather enough information to
   - Create a TestCase or Requirement WorkItem if one doesn't doesn't exist for the method/class
   - make available the data necessary to post process the junit result file to upload it to the XUnit importer


## Rationale

Many teams already have hundreds, if not thousands, of legacy tests in their test suites.  Asking them to generate
Requirements and TestCases in Polarion by hand is not only silly, it's just too time consuming.  Being able to
auto-generate a Polarion TestCase or Requirement given metadata that already exists in the source code is more ideal.
Furthermore, it is better to have a single source of truth (the source code) and have this reflected in Polarion, rather
than try to maintain and keep synchronized two sets of data (annotations in the source code, and TestCase/Requirements
in Polarion).


## Advantages

Why not just write requirements and testcases in polarion directly?

- Need an extra license for Product Manager to enter in information
- Need extra licenses for Developers to review in Polarion
- Bypasses the test project as the canonical source of truth
- Couples requirement/testcase definition to a specific implementation (Polarion)
- Polarion does not allow upstream communities to review or submit requirements/workitems
- Harder to review because it's not a plain text file (no diffs)
- At the mercy of eng-ops to update/restore information about a work item

Polarize solves all these problems

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
