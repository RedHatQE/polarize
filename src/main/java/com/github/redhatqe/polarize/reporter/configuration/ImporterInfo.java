package com.github.redhatqe.polarize.reporter.configuration;

import com.fasterxml.jackson.annotation.JsonProperty;


public class ImporterInfo {
    @JsonProperty
    private String endpoint;
    @JsonProperty
    private Selector selector;
    @JsonProperty
    private Integer timeout;
    @JsonProperty
    private Boolean enabled;

    public ImporterInfo() {
        this.endpoint = "";
        this.selector = new Selector();
        this.timeout = 300000;
        this.enabled = false;
    }

    public ImporterInfo(String ept, Selector sel, Integer to, Boolean enabled) {
        this.endpoint = ept;
        this.selector = sel;
        this.timeout = to;
        this.enabled = enabled;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public Selector getSelector() {
        return selector;
    }

    public void setSelector(Selector selector) {
        this.selector = selector;
    }

    public Integer getTimeout() {
        return timeout;
    }

    public void setTimeout(Integer timeout) {
        this.timeout = timeout;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }
}
