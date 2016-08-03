package com.redhat.qe.rhsm.importer;

import com.sun.org.apache.xpath.internal.operations.Bool;

import java.util.Map;

/**
 * Created by stoner on 8/3/16.
 */
public class TestSuitesProperties {
    private final String projectID;
    private final String user;
    private Boolean setTestRunFinished = Boolean.TRUE;
    private Boolean dryRun = Boolean.FALSE;
    private Boolean includeSkipped = Boolean.TRUE;
    private String responseName;
    private String testrunID;
    private String testrunTitle;
    private Map<String, String> customFields;


    public void setSetTestRunFinished(Boolean setTestRunFinished) {
        this.setTestRunFinished = setTestRunFinished;
    }

    public void setDryRun(Boolean dryRun) {
        this.dryRun = dryRun;
    }

    public void setIncludeSkipped(Boolean includeSkipped) {
        this.includeSkipped = includeSkipped;
    }

    public void setResponseName(String responseName) {
        this.responseName = responseName;
    }

    public void setTestrunID(String testrunID) {
        this.testrunID = testrunID;
    }

    public void setTestrunTitle(String testrunTitle) {
        this.testrunTitle = testrunTitle;
    }

    public void setCustomFields(Map<String, String> customFields) {
        this.customFields = customFields;
    }

    public void addCustromField(String key, String value) {
        this.customFields.put(key, value);
    }

    public String getProjectID() {
        return projectID;
    }

    public String getUser() {
        return user;
    }

    public Boolean getSetTestRunFinished() {
        return setTestRunFinished;
    }

    public Boolean getDryRun() {
        return dryRun;
    }

    public Boolean getIncludeSkipped() {
        return includeSkipped;
    }

    public String getResponseName() {
        return responseName;
    }

    public String getTestrunID() {
        return testrunID;
    }

    public String getTestrunTitle() {
        return testrunTitle;
    }

    public Map<String, String> getCustomFields() {
        return customFields;
    }

    public TestSuitesProperties (String id, String user) {
        this.projectID = id;
        this.user = user;
        this.responseName = null;
        this.testrunID = null;
        this.testrunTitle = null;
    }

    public TestSuitesProperties(String id, String user, String title, String testrunID) {
        this(id, user);
        this.testrunTitle = title;
        this.testrunID = testrunID;
    }
}
