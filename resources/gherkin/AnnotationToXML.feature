Feature: Generate XML from annotated class

  Polarion:
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