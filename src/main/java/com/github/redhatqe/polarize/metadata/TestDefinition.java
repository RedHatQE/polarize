package com.github.redhatqe.polarize.metadata;

import org.testng.annotations.Test;

import java.lang.annotation.*;

/**
 * Annotation to generate an XML file useable by the WorkItem Importer project.
 * </p>
 * The TestDefinitionProcessor will examine any method annotated with @TestDefinition in order to generate the mapping
 * between a test method in the source code, with the TestDefinition TestDefinition and Requirement ID.  This mapping
 * will then in turn be inserted in a post-processing step with the junit report file.
 * </p>
 * Since we are using the @Repeatable meta annotation, this is only useable on a JDK 1.8+.
 * </p>
 * Example Usage:
 * <code>
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
 * </code>
 */
@Repeatable(TestDefinitions.class)
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface TestDefinition {
    DefTypes.Project[] projectID();     // An array (actually set) of projects this definition applies to

    String[] testCaseID() default {""};
    //String[] documentPath() default "";   // Used to link the generated TestCase to a document
    String title() default "";          // If you don't use the default, must specify the xmlDesc path
    String description() default "";    // Must have description but may come from @Test
    String setup() default "";
    String teardown() default "";
    LinkedItem[] linkedWorkItems() default {};

    DefTypes.Importance importance() default DefTypes.Importance.HIGH;
    DefTypes.PosNeg posneg() default DefTypes.PosNeg.POSITIVE;
    DefTypes.Level level() default DefTypes.Level.SYSTEM;
    DefTypes.Automation automation() default DefTypes.Automation.AUTOMATED;
    String script() default "";                // path or name of automation script/method
    TestType testtype() default @TestType();

    // FIXME: In the TestCase importer, teststeps is actually just a string which seems wrong
    TestStep[] teststeps() default {};

    // TODO: Currently, there is no Requirements importer, so this is actually not used.  When a Requirement importer
    // is created, get rid of default
    Requirement[] reqs() default {};

    // Rarely used
    String assignee() default "";
    String initialEstimate() default "";
    String tags() default "";
    String component() default "";
    String subcomponent() default "";
    String upstream() default "";

    // These are not directly used by the importer
    String xmlDesc() default "";             // This is the path to the XML description file (the xml of the TestCase)
                                             // eg ${PROJECT_DIR}/testcases/{PROJECT}/{PACKAGE.CLASS.METHOD_NAME/xmlDesc
    boolean update() default false;          // If true, when xml description file exists, update with new one
}
