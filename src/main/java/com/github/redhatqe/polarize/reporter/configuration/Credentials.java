package com.github.redhatqe.polarize.reporter.configuration;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Credentials {
    @JsonProperty
    private String user;
    @JsonProperty
    private String password;

    public Credentials() {

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
}
