package com.github.redhatqe.polarize.configuration;

import com.fasterxml.jackson.annotation.JsonProperty;

public class TLSClient {
    @JsonProperty(value="keystore-path", required=true)
    private String keystorePath;

    @JsonProperty(value="keystorekey-pw", required=true)
    private String keystoreKeyPassword;

    @JsonProperty(value="keystore-pw", required=true)
    private String keystorePassword;

    @JsonProperty(value="truststore-path", required=true)
    private String truststorePath;

    @JsonProperty(value="truststore-pw", required=true)
    private String truststorePassword;

    public TLSClient(String kp, String kkp, String kpw, String tp, String tpw) {
        this.keystorePath = kp;
        this.keystoreKeyPassword = kkp;
        this.keystorePassword = kpw;
        this.truststorePath = tp;
        this.truststorePassword = tpw;
    }

    //"Copy" constructor
    public TLSClient(TLSClient orig) {
        this.keystorePath = orig.keystorePath;
        this.keystoreKeyPassword = orig.keystoreKeyPassword;
        this.keystorePassword = orig.keystorePassword;
        this.truststorePath = orig.truststorePath;
        this.truststorePassword = orig.truststorePassword;
    }

    public TLSClient() {

    }

    public String getKeystorePath() {
        return keystorePath;
    }

    public void setKeystorePath(String keystorePath) {
        this.keystorePath = keystorePath;
    }

    public String getKeystoreKeyPassword() {
        return keystoreKeyPassword;
    }

    public void setKeystoreKeyPassword(String keystoreKeyPassword) {
        this.keystoreKeyPassword = keystoreKeyPassword;
    }

    public String getKeystorePassword() {
        return keystorePassword;
    }

    public void setKeystorePassword(String keystorePassword) {
        this.keystorePassword = keystorePassword;
    }

    public String getTruststorePath() {
        return truststorePath;
    }

    public void setTruststorePath(String truststorePath) {
        this.truststorePath = truststorePath;
    }

    public String getTruststorePassword() {
        return truststorePassword;
    }

    public void setTruststorePassword(String truststorePassword) {
        this.truststorePassword = truststorePassword;
    }
}
