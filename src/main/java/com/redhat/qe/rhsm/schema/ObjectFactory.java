//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, v2.2.8-b130911.1802 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2016.06.30 at 02:05:36 PM EDT 
//


package com.redhat.qe.rhsm.schema;

import javax.xml.bind.annotation.XmlRegistry;


/**
 * This object contains factory methods for each 
 * Java content interface and Java element interface 
 * generated in the com.redhat.qe.rhsm.schema package. 
 * <p>An ObjectFactory allows you to programatically 
 * construct new instances of the Java representation 
 * for XML content. The Java representation of XML 
 * content can consist of schema derived interfaces 
 * and classes representing the binding of schema 
 * type definitions, element declarations and model 
 * groups.  Factory methods for each of these are 
 * provided in this class.
 * 
 */
@XmlRegistry
public class ObjectFactory {


    /**
     * Create a new ObjectFactory that can be used to create new instances of schema derived classes for package: com.redhat.qe.rhsm.schema
     * 
     */
    public ObjectFactory() {
    }

    /**
     * Create an instance of {@link TestCaseMetadata }
     * 
     */
    public TestCaseMetadata createTestCaseMetadata() {
        return new TestCaseMetadata();
    }

    /**
     * Create an instance of {@link TestcaseType }
     * 
     */
    public TestcaseType createTestcaseType() {
        return new TestcaseType();
    }

    /**
     * Create an instance of {@link TestCaseMetadata.Workitems }
     * 
     */
    public TestCaseMetadata.Workitems createTestCaseMetadataWorkitems() {
        return new TestCaseMetadata.Workitems();
    }

    /**
     * Create an instance of {@link ReqType }
     * 
     */
    public ReqType createReqType() {
        return new ReqType();
    }

    /**
     * Create an instance of {@link TeststepType }
     * 
     */
    public TeststepType createTeststepType() {
        return new TeststepType();
    }

    /**
     * Create an instance of {@link TestcaseType.Caseimportance }
     * 
     */
    public TestcaseType.Caseimportance createTestcaseTypeCaseimportance() {
        return new TestcaseType.Caseimportance();
    }

    /**
     * Create an instance of {@link TestcaseType.Caselevel }
     * 
     */
    public TestcaseType.Caselevel createTestcaseTypeCaselevel() {
        return new TestcaseType.Caselevel();
    }

    /**
     * Create an instance of {@link TestcaseType.Testtype }
     * 
     */
    public TestcaseType.Testtype createTestcaseTypeTesttype() {
        return new TestcaseType.Testtype();
    }

    /**
     * Create an instance of {@link TestcaseType.Caseposneg }
     * 
     */
    public TestcaseType.Caseposneg createTestcaseTypeCaseposneg() {
        return new TestcaseType.Caseposneg();
    }

    /**
     * Create an instance of {@link TestcaseType.Caseautomation }
     * 
     */
    public TestcaseType.Caseautomation createTestcaseTypeCaseautomation() {
        return new TestcaseType.Caseautomation();
    }

    /**
     * Create an instance of {@link TestcaseType.Status }
     * 
     */
    public TestcaseType.Status createTestcaseTypeStatus() {
        return new TestcaseType.Status();
    }

    /**
     * Create an instance of {@link TestcaseType.Teststeps }
     * 
     */
    public TestcaseType.Teststeps createTestcaseTypeTeststeps() {
        return new TestcaseType.Teststeps();
    }

}
