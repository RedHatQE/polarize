package com.github.redhatqe.polarize.cimetrics;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CITestResult {
    @JsonProperty(required = true)
    private String executor;
    @JsonProperty(required = true)
    private String arch;  // FIXME:  This should be enum
    @JsonProperty(required = true)
    private Integer executed;
    @JsonProperty(required = true)
    private Integer failed;
    @JsonProperty(required = true)
    private Integer passed;
}
