Feature: Generate XML from annotated class

  Polarion:
    Severity: must_have      # One of should_have, must_have, nice_to_have, will_not_have
    Type: Functional         # One of Functional, NonFunctional,
    SubType:                 # optional
    Status: draft            # defaults to draft
    TestName:                # Not filled in by PM.  Done by QE
    PlannedIn                # a list of plannedin releases
      - RHEL_7_3
    Links
      - /some/link/to/share  # optional

  Scenario: Annotated class generates valid XML
    Given The class is annotated with @Polarion
      And The Polarion annotation has author
      And The Polarion annotation has projectId
      And The Polarion annotation has sub-annotation Requirement
     Then XML