package com.github.redhatqe.polarize.configuration;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Created by stoner on 5/26/17.
 */
public class MessageOpts {
    public static final Integer INFINITE = -1;
    @JsonProperty
    private Long timeout;
    @JsonProperty
    private Integer maxMsgs;

    public Long getTimeout() {
        return timeout;
    }

    public void setTimeout(Long timeout) {
        this.timeout = timeout;
    }

    public Integer getMaxMsgs() {
        return maxMsgs;
    }

    public void setMaxMsgs(Integer maxMsgs) {
        this.maxMsgs = maxMsgs;
    }


    public MessageOpts(Long to, Integer maxMsgs) {
        this.timeout = to;
        this.maxMsgs = maxMsgs;
    }

    public MessageOpts() {

    }
}
