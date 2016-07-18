# Vision

Requirements and test case definitions should be easy to review, maintain, collaborate, be decoupled from a specific 
test case management tool, and be publicly visible to upstream community.

# Mission

Create a working group that will create tools to help test teams easily create requirement and test case definitions
that can be imported into Polarion (or any other test management system)

# Goals

With the vision stated above, work on a project called polarize has already been started.  Since our team is a JVM based
team, polarize currently only addresses the needs for java teams.  However, similar tools have been written for python
teams as well.  Polarize was written to address the shortcomings and disadvantages of directly writing the requirements
and test case definitions into Polarion.  The main goals are:

1. Provide tools that extract metadata in source code to generate an XML file suitable for use by the WorkItem Importer
   tool to generate a Polarion TestCase and/or Requirement
2. Provide tool to parse a feature file that will generate an XML file suitable for use by WorkItem importer project
   to generate a Polarion Requirement
3. The description file will act as a map from test method to Polarion TestCase ID so that the property elements in the
   XUnit Importer can be filled in
4. A tool to post process an XUnit file with the needed property elements
5. A workflow process to generate requirements from feature files


# Expectations

There are many disadvantages to simply writing requirements or test cases directly into Polarion.  Here is a list of
the disadvantages of not using this project, as well as the advantages if the project comes to fruition:

## Why not just write requirements and test cases directly in Polarion?

- Need an extra license for Product Manager to enter in information
- Need extra licenses for Developers to review in Polarion
- Bypasses the test project as the canonical source of truth
- Tightly couples requirement/testcase definition to a specific implementation (Polarion)
- Polarion does not allow upstream communities to review or submit requirements/workitems
- Harder to review because it's not a plain text file (no diffs)
- At the mercy of eng-ops to update/restore information about a work item if there is a problem

## Polarize solves all these problems

- Because requirements/testcases are external files, we don't need extra licenses to review or create
- Because definitions are external files, they can be kept in source control (single canon of truth)
- Decouples definition of requirements and testcases from a specific implementation
- Because requirements and testcases belong in source control, they can be reviewed by upstream community
- Since annotations generate plain text xml, it is easy to diff and review
- Because the xml descriptions are external, they can always be used to update existing tests on-demand

Furthermore, test case and requirements can be generated automatically whenever code is compiled.  This does not mean
that the imported WorkItem is automatically approved.  It just means they will be imported into Polarion in a draft
state.

- Several sites recommend as best practice to keep requirements in source control
  - Test runners like cucumber and jbehave exist which require access to the feature file
  - http://www.testingexcellence.com/bdd-guidelines-best-practices/
  - http://sqa.stackexchange.com/questions/13780/cucumber-where-do-you-store-your-feature-files-cukes
  - https://github.com/cucumber/cucumber/wiki/Cucumber-Backgrounder
- Does not require execution of a TestRun to generate Polarion TestCase or Requirement
- Since it is Java based, any test team using java can use or modify it according to their needs
  - Similar projects to polarize for python are in the works by other teams or exist already


# Preliminary Plan

The polarize project can be used as a starting point.  For other languages like python, an agreed upon format for the
metadata can be decided upon.  If I am not mistaken there are 2 current python approaches to doing something similar to 
what polarize does.  In one approach, the metadata is stored in reStructuredText files that map to a test method and the
metadata is parsed out.  In the second approach, the metadata is stored directly as docstrings and then parsed out.  In
either case, there should be a standardized way for a python based test team to have metadata about their tests.

Please see https://github.com/rarebreed/polarize for the initial work done on this project.

# Budget Estimation

2-3 QE familiar with java and python (and perhaps ruby) spending at least 50% of their time on this project.  Perhaps
4-6 months of work for java and python support.  Additional time needed to support other languages.

# Risk Mitigation Plan

The main risk I see is the time involved for engineers involved in this project.  The only perceived benefit of directly
entering in the information inside of Polarion via the GUI is far outweighed by all the advantages listed above.

Another risk is the many languages at use in Red Hat.  For example, there are a handful of teams using ruby, javascript
or even C(++).  Each language would need to have its own way of storing the metadata.  In the worst case scenario, the
XML description file can directly be written for each test rather than being auto-generated by some language facility
(for example java's annotations, python's docstrings, or clojure metadata).  

Generating requirements requires cooperation from the Product Managers and Developers.  There needs to be a back and 
forth discussion between the PM, devs and QE (and possibly upstream committers) while creating requirements or test case
definitions.  This collaboration is a cultural and workflow process element and is therefore beyond the technical merits
of this project.  However, this project is also envisioned as a culture/workflow process and so there is risk in
bringing people outside of QE into this way of doing things.