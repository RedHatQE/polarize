package com.github.redhatqe.polarize.configuration;


import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.redhatqe.polarize.reporter.configuration.ImporterInfo;

public class TestCaseInfo extends ImporterInfo {
    @JsonProperty
    private String prefix;
    @JsonProperty
    private String suffix;

    public TestCaseInfo() {

    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public String getSuffix() {
        return suffix;
    }

    public void setSuffix(String suffix) {
        this.suffix = suffix;
    }
}
