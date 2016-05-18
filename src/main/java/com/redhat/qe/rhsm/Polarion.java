package com.redhat.qe.rhsm;

import java.lang.annotation.Repeatable;

/**
 * Created by stoner on 5/17/16.
 */
@Repeatable(Polarions.class)
public @interface Polarion {
    public String xmlConfig();          // path to xml file which will override all values if it exists
    public String projectID();
    public String testCaseID();
    public String[] requirementIDs();   // the ID:pathToFeatureFile
    public CaseType caseType();         // CaseType is an enum
    public String component();
    public Importance importance();
}
