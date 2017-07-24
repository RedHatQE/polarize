package com.github.redhatqe.polarize.configuration;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Created by stoner on 5/17/17.
 */
public class Broker {
    @JsonProperty
    String url;
    @JsonProperty
    String user;
    @JsonProperty
    String password;
    @JsonProperty
    MessageOpts messages;

    public Broker(String url, String u, String pw, Long to, Integer nummsgs) {
        this.url = url;
        this.user = u;
        this.password = pw;
        this.messages = new MessageOpts(to, nummsgs);
    }

    public Broker() {

    }

    public Broker(Broker orig) {
        this.url = orig.getUrl();
        this.user = orig.getUser();
        this.password = orig.getPassword();
        this.messages = new MessageOpts(orig.getMessageTimeout(), orig.getMessageMax());
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public MessageOpts getMessages() { return this.messages; }

    public void setMessages(MessageOpts opts) { this.messages = opts; }

    @JsonIgnore
    public Long getMessageTimeout() { return this.messages.getTimeout(); }

    @JsonIgnore
    public Integer getMessageMax() { return this.messages.getMaxMsgs(); }

    @JsonIgnore
    public void setMessageTimeout(Long to) { this.messages.setTimeout(to); }

    @JsonIgnore
    public void setMessageMax(Integer max) { this.messages.setMaxMsgs(max); }
}
