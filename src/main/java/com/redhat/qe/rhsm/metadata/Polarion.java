package com.redhat.qe.rhsm.metadata;

import java.lang.annotation.Repeatable;

/**
 * Created by stoner on 5/6/16.
 */
@Repeatable(Polarions.class)
public @interface Polarion {
    String author() default "CI User";
    String projectID();
    String xmlConfig() default "";
    String testCaseID() default "";
    String caseimportance() default "high";
    String caseposneg() default "positive";
    String caselevel() default "component";
    String testtype() default "functional";
    Requirement[] reqs();  // eg. requirementIDs = {"RHEL6-25678", "RHEL6-27654"}
    String setup() default "";
    String teardown() default "";
}
