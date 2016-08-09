package com.redhat.qe.rhsm.metadata;

import java.lang.annotation.*;

/**
 * Created by stoner on 5/6/16.
 *
 * Annotation to generate an XML file useable by the WorkItem Importer project that Sim's team is working on.
 * The TestDefinitionProcessor will examine any method annotated with @TestDefinition in order to generate the mapping
 * between a test method in the source code, with the TestDefinition TestDefinition and Requirement ID.  This mapping
 * will then in turn be inserted in a post-processing step with the junit report file.
 *
 * Since we are using the @Repeatable meta annotation, this is only useable on a JDK 1.8+.
 *
 * Example Usage:
 *
 *     @TestCase(author="Sean Toner",                 // required
 *               projectId="RedHatEnterpriseLinux7",  // required
 *               xmlDesc="/path/to/xml-description",  // defaults to ""
 *               testCaseID="RHEL7-56743,             // if empty or null, make request to WorkItem Importer tool
 *               caseimportance="high",               // defaults to high  if not given
 *               caseposneg="positive",               // defaults to positive if not given
 *               caselevel="component",               // defaults to component if not given
 *               testtype="functional",               // defaults to functional if not given
 *               reqs = {@Requirement(id="",
 *                                    severity="Should Have",         // defaults to Must Have
 *                                    reqtype="NonFunctional",        // defaults to Functional
 *                                    priority="high",                // defaults to medium
 *                                    author="Sean Toner",            // required (someone has to OK this)
 *                                    description="Feature Summary",  // required
 *                                    xmlDesc="/path/to/xml-file")}
 *               setup="Description of any preconditions that must be established for test case to run"
 */
@Repeatable(TestDefinitions.class)
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface TestDefinition {
    String author() default "CI User";
    String projectID();
    String xmlDesc() default "";
    String testCaseID() default "";
    String caseimportance() default "high";
    String caseposneg() default "positive";
    String caselevel() default "component";
    String testtype() default "functional";
    String description() default "";           // Must have description but may come from @Test
    String setup() default "";
    String teardown() default "";
    boolean override() default true;          // If true, when xml description file exists, generate a new one.
    TestStep[] teststeps() default {};
    Requirement[] reqs();  // eg. requirementIDs = {"RHEL6-25678", "RHEL6-27654"}
}
