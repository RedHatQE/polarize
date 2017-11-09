package com.github.redhatqe.polarize.configuration;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.redhatqe.byzantine.configuration.IConfig;
import com.github.redhatqe.byzantine.configuration.Serializer;
import com.github.redhatqe.byzantine.exceptions.InvalidCLIArg;
import com.github.redhatqe.byzantine.parser.Setter;


import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;


/**
 * A simple configuration class to set required parameters such as broker urls or timeouts.  An example JSON configuration
 * is in src/main/resources/skeleton.json, and a YAML configuration is in src/main/resources/skeleton.yaml
 */
public class BrokerConfig implements IConfig {
    // =========================================================================
    // 1. Add all properties for your class/configuration
    // =========================================================================
    @JsonProperty
    private Map<String, Broker> brokers;
    @JsonProperty
    private String defaultBroker;

    // ==========================================================================
    // 2. Add all fields not belonging to the configuration here
    // ==========================================================================
    @JsonIgnore
    public Map<String, Setter<String>> handlers = new HashMap<>();
    @JsonIgnore
    public Boolean showHelp = false;

    // =========================================================================
    // 3. Constructors go here.  Remember that there must be a no-arg constructor and you usually want a copy con
    // =========================================================================
    public BrokerConfig(String name, String url, String user, String pw, Long to, Integer max) {
        this();
        Broker broker = new Broker(url, user, pw, to, max);
        this.brokers.put(name, broker);
        this.defaultBroker = name;
    }

    public BrokerConfig() {
        this.brokers = new HashMap<>();
        this.defaultBroker = "ci";
        this.setupDefaultHandlers();
    }

    /**
     * Create a new BrokerConfig with the same values as the instance passed in
     */
    public BrokerConfig(BrokerConfig cfg) {
        this();
        cfg.getBrokers().forEach((k, v) -> this.brokers.put(k, new Broker(v)));
        this.defaultBroker = cfg.defaultBroker;
        this.setupDefaultHandlers();
    }

    //=============================================================================
    // 4. Define the bean setters and getters for all fields in #1
    //=============================================================================
    public Map<String, Broker> getBrokers() {
        return this.brokers;
    }

    public void setBrokers(Map<String, Broker> b) {
        this.brokers = b;
    }

    public String getDefaultBroker() {
        return this.defaultBroker;
    }

    public void setDefaultBroker(String def) {
        this.defaultBroker = def;
    }

    //=============================================================================
    // 5. Define any functions for parsing the value of a command line opt and setting the values
    //=============================================================================
    public void addBroker(String name, Broker b) {
        this.brokers.put(name, b);
    }

    /**
     * This function reads in the value from the --broker option
     *
     * This value should take the form of key.field=val.  For example:
     *   --broker ci.url=192.168.0.1
     * @param val
     */
    public void parseBroker(String val) {
        String[] tokens = val.split("=", 2);
        if (tokens.length != 2)
            throw new InvalidCLIArg("Argument to --broker must be in form key.field=val");
        String value = tokens[1];

        String[] brokerAndField = tokens[0].split("\\.");
        if (brokerAndField.length != 2)
            throw new InvalidCLIArg("Argument to --broker must be in form key.field[.subfield]=val");
        String brokerArg = brokerAndField[0];
        String field = brokerAndField[1];

        Broker b = this.brokers.get(brokerArg);
        switch(field) {
            case "url":
                b.setUrl(value);
                break;
            case "user":
                b.setUser(value);
                break;
            case "password":
                b.setPassword(value);
                break;
            case "timeout":
                b.setMessageTimeout(Long.parseLong(value));
                break;
            case "max":
                b.setMessageMax(Integer.parseInt(value));
                break;
            default:
                break;
        }
    }

    //=============================================================================
    // 6. implement the methods from IConfig
    //=============================================================================
    @Override
    public void setupDefaultHandlers() {
        this.addHandler(ConfigOpts.BROKER, this::parseBroker, this.handlers);
        this.addHandler(ConfigOpts.DEFAULT_BROKER, this::setDefaultBroker, this.handlers);
        this.addHandler(ConfigOpts.EDIT_CONFIG, this::writeConfig, this.handlers);
    }

    @Override
    public Map<String, Setter<String>> sGetHandlers() {
        return this.handlers;
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
        this.showHelp = help;
    }


    @Override
    public <T> Setter<T> dispatch(String s, Map<String, Setter<T>> map) {
        return map.get(s);
    }


    public static void main(String[] args) {
        BrokerConfig cfg = new BrokerConfig("ci", "ci-labs.eng.rdu2:61616", "stoner", "foo", 300000L, 1);
        BrokerConfig cfg2 = new BrokerConfig("ci", "ci-labs-foo", "stoner", "bar", 60000L, -1);
        //cfg2.parse(args);

        Broker b = new Broker("ci-labs.eng.rdu2:61613", "foo", "bar", 1000L, 1);
        cfg.addBroker("metrics", b);
        cfg2.addBroker("metrics", b);
        try {
            String testpath = "/tmp/testing.json";
            Serializer.toJson(cfg, testpath);
            Serializer.toYaml(cfg, "/tmp/testing.yaml");
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            BrokerConfig readIn = Serializer.fromJson(BrokerConfig.class, new File("/tmp/testing.json"));
            Serializer.toYaml(cfg2, "/tmp/testing2.yaml");
            BrokerConfig cfgYaml = Serializer.fromYaml(BrokerConfig.class, new File("/tmp/testing2.yaml"));
            Broker broker = readIn.getBrokers().get("ci");
            System.out.println(readIn.getDefaultBroker());
            System.out.println(cfgYaml.getBrokers().get("ci").getUrl());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
