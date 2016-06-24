package com.redhat.qe.rhsm;


import com.redhat.qe.rhsm.metadata.Polarion;
import com.redhat.qe.rhsm.metadata.Requirement;

/**
 * Created by stoner on 6/22/16.
 */
public class TestAnnotation {
    TestAnnotation() {

    }

    @Polarion(projectID="RHEL6",
    xmlConfig="/path/to/xml",
    reqs={})
    @Polarion(projectID="RedHatEnterpriseLinux7",
              reqs = {@Requirement(polarionId="",
                                   severity="Should Have",         // defaults to Must Have
                                   reqtype="NonFunctional",        // defaults to Functional
                                   priority="high",                // defaults to medium
                                   author="Sean Toner",            // required (someone has to OK this)
                                   description="Feature Summary",  // required
                                   feature="/path/to/feature")})
    public void testMethod() {
        System.out.println("Just a test");
    }
}
