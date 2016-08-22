package com.github.redhatqe.polarize;


import com.github.redhatqe.polarize.metadata.TestDefinition;
import com.github.redhatqe.polarize.metadata.Requirement;

/**
 * Created by stoner on 6/22/16.
 */
public class TestAnnotation {
    TestAnnotation() {

    }

    @TestDefinition(projectID="RHEL6",
              xmlDesc="/path/to/xml",
              reqs={@Requirement(xmlDesc="/path/to/xml-requirement")})
    @TestDefinition(projectID="RedHatEnterpriseLinux7",
              reqs = {@Requirement(id="",
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
