package com.github.redhatqe.polarize.junitreporter;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration class for the XUnitReporter
 */
public class ReporterConfig {
    private String author;
    private String projectID;
    private String responseName;
    private Boolean includeSkipped = false;
    private Boolean dryRun = false;
    private Boolean setTestRunFinished = true;

    private String testrunID = "";
    private String testrunTitle = "";
    private String testcasesXMLPath = "";
    private String requirementsXMLPath = "";

    public String getTestcasesXMLPath() {
        return testcasesXMLPath;
    }

    public void setTestcasesXMLPath(String testcaseXMLPath) {
        this.testcasesXMLPath = testcaseXMLPath;
    }

    public String getRequirementsXMLPath() {
        return requirementsXMLPath;
    }

    public void setRequirementsXMLPath(String requirementsXMLPath) {
        this.requirementsXMLPath = requirementsXMLPath;
    }

    public Map<String, String> getCustomFields() {
        return customFields;
    }

    public void setCustomFields(Map<String, String> customFields) {
        this.customFields = customFields;
    }

    public void setCustomFields(String key, String val) {
        if (this.customFields == null)
            this.customFields = new HashMap<>();
        this.customFields.put(key, val);
    }

    private Map<String, String> customFields;

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getProjectID() {
        return projectID;
    }

    public void setProjectID(String projectID) {
        this.projectID = projectID;
    }

    public String getResponseName() {
        return responseName;
    }

    public void setResponseName(String responseName) {
        this.responseName = responseName;
    }

    public Boolean getIncludeSkipped() {
        return includeSkipped;
    }

    public void setIncludeSkipped(Boolean includeSkipped) {
        this.includeSkipped = includeSkipped;
    }

    public Boolean getDryRun() {
        return dryRun;
    }

    public void setDryRun(Boolean dryRun) {
        this.dryRun = dryRun;
    }

    public Boolean getSetTestRunFinished() {
        return setTestRunFinished;
    }

    public void setSetTestRunFinished(Boolean setTestRunFinished) {
        this.setTestRunFinished = setTestRunFinished;
    }

    public String getTestrunID() {
        return testrunID;
    }

    public void setTestrunID(String testrunID) {
        this.testrunID = testrunID;
    }

    public String getTestrunTitle() {
        return testrunTitle;
    }

    public void setTestrunTitle(String testrunTitle) {
        this.testrunTitle = testrunTitle;
    }


}
