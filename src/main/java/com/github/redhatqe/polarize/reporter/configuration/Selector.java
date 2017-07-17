package com.github.redhatqe.polarize.reporter.configuration;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Selector {
    @JsonProperty
    private String name;
    @JsonProperty
    private String value;

    public Selector() {
        this.name = "";
        this.value = "";
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
