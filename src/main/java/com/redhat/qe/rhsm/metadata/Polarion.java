package com.redhat.qe.rhsm.metadata;

import java.lang.annotation.Repeatable;

/**
 * Created by stoner on 5/6/16.
 */
@Repeatable(Polarions.class)
public @interface Polarion {
    String testConfigXml();     // Path to XML description of a TestCase for use by WorkItem Importer
    String reqConfigXml();      // Path to XML description of a Requirement for use by WorkItem Importer
    String featureFile();       // Path to gherkin style feature file
    Requirement requirement();  // eg. requirementIDs = {"RHEL6-25678", "RHEL6-27654"}
    String setup();
    String teardown();
    String[] teststeps();
}
