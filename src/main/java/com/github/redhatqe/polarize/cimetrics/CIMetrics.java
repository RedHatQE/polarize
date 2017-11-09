package com.github.redhatqe.polarize.cimetrics;


import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.redhatqe.byzantine.configuration.IConfig;
import com.github.redhatqe.byzantine.parser.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * This class represents the data we will send
 *
 * {
 *   "component" : "subscription-manager-${SUBMAN_VERSION}",  // TODO: How do we get the version of subscription-manager we are testing?  From CI_MESSAGE I suppose that information is in the brew info?
 *   "trigger" : "git push",  // TODO: Can get this from curl -u ops-qe-jenkins-ci-automation:334c628e5e5df90ae0fabb77db275c54 -k <BUILD_URL> and look for [actions] -> [causes] -> shortDescription
 *   "tests" : [ // FIXME:  Ok, this is dumb.  All this information is in the polarion-testng.xml already.  Need to parse the XML file for this data
 *               // Also need to clarify what "executor" is
 *               { "executor" : "beaker" , "arch" : "aarch64" , "executed" : 20, "failed" : 1, "passed" : 19 },
 *               { "executor" : "CI-OSP" , "arch" : "x86_64" , "executed" : 1000,   "failed" : 54, "passed" : 946 }
 *             ],
 *   "jenkins_job_url" : "${JENKINS_URL}",
 *   "jenkins_build_url" : "${BUILD_URL}",
 *   "logstash_url" : "",  // TODO: Ask boaz what this url is
 *   "CI_tier" : 1, // FIXME: Do we have a var that indicates this?  Our job name itself tells what tier the test is for
 *   "base_distro" : "RHEL 7.2+updates",  // TODO: Should be from DISTRO var unless they want a specific format
 *   "brew_task_id" : "11610928",  // TODO: need to parse the CI_MESSAGE text and see if it is in there
 *   "compose_id" : "NULL",  // Is there a way to get this?  Seems to only for use case 3 (eg nightly testing)
 *   "create_time" : "2016-08-18T20:13:51Z",  // TODO: This should be part of the polarion-testng.xml.  Not sure why they need this.  Need to extract from xml
 *   "completion_time" : "2016-08-18T20:52:10Z",  // TODO: Same as above
 *   "CI_infra_failure" : "DNS",  // FIXME: Clarify what this is for
 *   "CI_infra_failure_desc" : "Can't resolve slave name to IP address", // FIXME:  see above
 *   "job_name" : "${JOB_NAME}",
 *   "build_type" : "official",
 *   "team" : "rhsm-qe",
 *   "recipients" : ["jsefler", "jmolet", "reddaken", "shwetha", "jstavel"],
 *   "artifact": ? // TODO: Not sure what artifact to put here.  The polarion results?  the testng.xml?
 * }
 */
public class CIMetrics implements IConfig {
    // =========================================================================
    // 1. Add all properties for your class/configuration
    // =========================================================================
    @JsonProperty
    private String component;
    @JsonProperty
    private String trigger;
    @JsonProperty
    private List<CITestResult> tests;
    @JsonProperty(value="jenkins_job_url")
    private String jenkinsJobUrl;
    @JsonProperty(value="jenkins_build_url")
    private String jenkinsBuildUrl;
    @JsonProperty(value="logstash_url")
    private String logstashUrl;
    @JsonProperty(value="CI_tier")
    private Integer CITier;
    @JsonProperty(value="base_distro")
    private String baseDistro;
    @JsonProperty(value="brew_task_id")
    private Integer brewTaskID;
    @JsonProperty(value="compose_id")
    private String composeID;
    @JsonProperty(value="create_time")
    private String createTime;
    @JsonProperty(value="completion_time")
    private String completionTime;
    @JsonProperty(value="CI_infra_failure")
    private String CIInfraFailure;
    @JsonProperty(value="CI_infra_failure_desc")
    private String CIInfraFailureDesc;
    @JsonProperty(value="job_name")
    private String jobName;
    @JsonProperty(value="build_type")
    private String buildType;
    @JsonProperty
    private String team;
    @JsonProperty
    private List<String> recipients;
    @JsonProperty
    private String artifact;

    // ==========================================================================
    // 2. Add all fields not belonging to the configuration here
    // ==========================================================================

    // =========================================================================
    // 3. Constructors go here.  Remember that there must be a no-arg constructor
    // =========================================================================
    public CIMetrics() {
        this.tests = new ArrayList<>();
        this.recipients = new ArrayList<>();
    }

    public CIMetrics(CIMetrics orig) {
        this();
        this.tests = new ArrayList<>(orig.tests);
        this.recipients = new ArrayList<>(orig.recipients);
    }

    //=============================================================================
    // 4. Define the bean setters and getters for all fields in #1
    //=============================================================================
    public String getComponent() {
        return component;
    }

    public void setComponent(String component) {
        this.component = component;
    }

    public String getTrigger() {
        return trigger;
    }

    public void setTrigger(String trigger) {
        this.trigger = trigger;
    }

    public List<CITestResult> getTests() {
        return tests;
    }

    public void setTests(List<CITestResult> tests) {
        this.tests = tests;
    }

    public String getJenkinsJobUrl() {
        return jenkinsJobUrl;
    }

    public void setJenkinsJobUrl(String jenkinsJobUrl) {
        this.jenkinsJobUrl = jenkinsJobUrl;
    }

    public String getJenkinsBuildUrl() {
        return jenkinsBuildUrl;
    }

    public void setJenkinsBuildUrl(String jenkinsBuildUrl) {
        this.jenkinsBuildUrl = jenkinsBuildUrl;
    }

    public String getLogstashUrl() {
        return logstashUrl;
    }

    public void setLogstashUrl(String logstashUrl) {
        this.logstashUrl = logstashUrl;
    }

    public Integer getCITier() {
        return CITier;
    }

    public void setCITier(Integer CITier) {
        this.CITier = CITier;
    }

    public String getBaseDistro() {
        return baseDistro;
    }

    public void setBaseDistro(String baseDistro) {
        this.baseDistro = baseDistro;
    }

    public Integer getBrewTaskID() {
        return brewTaskID;
    }

    public void setBrewTaskID(Integer brewTaskID) {
        this.brewTaskID = brewTaskID;
    }

    public String getComposeID() {
        return composeID;
    }

    public void setComposeID(String composeID) {
        this.composeID = composeID;
    }

    public String getCreateTime() {
        return createTime;
    }

    public void setCreateTime(String createTime) {
        this.createTime = createTime;
    }

    public String getCompletionTime() {
        return completionTime;
    }

    public void setCompletionTime(String completionTime) {
        this.completionTime = completionTime;
    }

    public String getCIInfraFailure() {
        return CIInfraFailure;
    }

    public void setCIInfraFailure(String CIInfraFailure) {
        this.CIInfraFailure = CIInfraFailure;
    }

    public String getCIInfraFailureDesc() {
        return CIInfraFailureDesc;
    }

    public void setCIInfraFailureDesc(String CIInfraFailureDesc) {
        this.CIInfraFailureDesc = CIInfraFailureDesc;
    }

    public String getJobName() {
        return jobName;
    }

    public void setJobName(String jobName) {
        this.jobName = jobName;
    }

    public String getBuildType() {
        return buildType;
    }

    public void setBuildType(String buildType) {
        this.buildType = buildType;
    }

    public String getTeam() {
        return team;
    }

    public void setTeam(String team) {
        this.team = team;
    }

    public List<String> getRecipients() {
        return recipients;
    }

    public void setRecipients(List<String> recipients) {
        this.recipients = recipients;
    }

    public String getArtifact() {
        return artifact;
    }

    public void setArtifact(String artifact) {
        this.artifact = artifact;
    }

    //=============================================================================
    // 5. Define any functions for parsing the value of a command line opt and setting the values
    //=============================================================================
    public void getJU() {
        String ju = System.getenv("JENKINS_URL");
        this.jenkinsJobUrl = ju == null ? "" : ju;
    }

    public void getJBU() {
        String jbu = System.getenv("BUILD_URL");
        this.jenkinsBuildUrl =  jbu == null ? "" : jbu;
    }

    //=============================================================================
    // 6. implement the methods from IConfig
    //=============================================================================
    @Override
    public void setupDefaultHandlers() {

    }

    @Override
    public Map<String, Setter<String>> sGetHandlers() {
        return null;
    }

    @Override
    public Map<String, Setter<Boolean>> bGetHandlers() {
        return null;
    }

    @Override
    public Map<String, Setter<Integer>> iGetHandlers() {
        return null;
    }

    @Override
    public void setHelp(Boolean help) {

    }


}
