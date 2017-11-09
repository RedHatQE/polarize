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
    @JsonProperty
    TLSClient tls;

    public Broker(String url, String u, String pw, Long to, Integer nummsgs, TLSClient tls) {
        this.url = url;
        this.user = u;
        this.password = pw;
        this.messages = new MessageOpts(to, nummsgs);
        this.tls = new TLSClient(tls);
    }

    public Broker(String url, String u, String pw, Long to, Integer nummsgs) {
        this.url = url;
        this.user = u;
        this.password = pw;
        this.messages = new MessageOpts(to, nummsgs);
        this.tls = new TLSClient();
    }

    public Broker() {

    }

    public Broker(Broker orig) {
        this.url = orig.getUrl();
        this.user = orig.getUser();
        this.password = orig.getPassword();
        this.messages = new MessageOpts(orig.getMessageTimeout(), orig.getMessageMax());
        this.tls = new TLSClient(tls);
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

    @JsonIgnore
    public String getKeystorePath() { return this.tls.getKeystorePath(); }

    @JsonIgnore
    public String getKeystorePassword() { return this.tls.getKeystorePassword(); }

    @JsonIgnore
    public String getKeystoreKeyPassword() { return this.tls.getKeystoreKeyPassword(); }

    @JsonIgnore
    public String getTruststorePath() { return this.tls.getTruststorePath(); }

    @JsonIgnore
    public String getTruststorePassword() { return this.tls.getTruststorePassword(); }

    @JsonIgnore
    public void setKeystorePath(String path) { this.tls.setKeystorePath(path); }

    @JsonIgnore
    public void setKeystorePassword(String pw) { this.tls.setKeystorePassword(pw); }

    @JsonIgnore
    public void setKeystoreKeyPassword(String pw) { this.tls.setKeystoreKeyPassword(pw); }

    @JsonIgnore
    public void setTruststorePath(String path) { this.tls.setTruststorePath(path); }

    @JsonIgnore
    public void setTruststorePassword(String pw) { this.tls.setTruststorePassword(pw); }
}
