package com.redhat.qe.rhsm;


import com.redhat.qe.rhsm.metadata.Polarion;
import com.redhat.qe.rhsm.metadata.Requirement;

/**
 * Created by stoner on 6/22/16.
 */
@Polarion(projectID="RedHatEnterpriseLinux7",
          requirement = @Requirement(polarionId="",
                                     severity="Should Have",         // defaults to Must Have
                                     reqtype="NonFunctional",        // defaults to Functional
                                     priority="high",                // defaults to medium
                                     author="Sean Toner",            // required (someone has to OK this)
                                     description="Feature Summary",  // required
                                     feature="/path/to/feature"))
public class TestAnnotation {

}
