package com.github.redhatqe.polarize.configuration;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.redhatqe.byzantine.config.Serializer;
import com.github.redhatqe.byzantine.parser.Setter;
import com.github.redhatqe.polarize.exceptions.XMLUnmarshallError;
import com.github.redhatqe.polarize.reporter.configuration.ReporterConfig;
import com.github.redhatqe.polarize.reporter.importer.xunit.Testsuites;
import com.github.redhatqe.polarize.reporter.jaxb.IJAXBHelper;
import com.github.redhatqe.polarize.reporter.jaxb.JAXBHelper;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;


public class FullConfig extends ReporterConfig {
    // =========================================================================
    // 1. Add all properties for your class/config
    // =========================================================================
    @JsonProperty
    private TestCaseInfo testcase;

    // ==========================================================================
    // 2. Add all fields not belonging to the config here
    // ==========================================================================
    @JsonIgnore
    public Map<String, Setter<String>> handlers = new HashMap<>();
    @JsonIgnore
    Testsuites suites;

    // =========================================================================
    // 3. Constructors go here.  Remember that there must be a no-arg constructor
    // =========================================================================
    public FullConfig() {
        super();
    }

    //=============================================================================
    // 4. Define the bean setters and getters for all fields in #1
    //=============================================================================
    public TestCaseInfo getTestcase() {
        return testcase;
    }

    public void setTestcase(TestCaseInfo testcase) {
        this.testcase = testcase;
    }

    //=============================================================================
    // 5. Define any functions for parsing the value of a command line opt and setting the values
    //=============================================================================
    public Testsuites getTestSuites(File xunitPath) {
        JAXBHelper jaxb = new JAXBHelper();
        Optional<Testsuites> ts = IJAXBHelper.unmarshaller(Testsuites.class, xunitPath,
                jaxb.getXSDFromResource(Testsuites.class));
        this.suites =  ts.orElseThrow(() -> new XMLUnmarshallError("Could not unmarshall xunit file"));
        return this.suites;
    }




    //=============================================================================
    // 6. implement the methods from IConfig
    //=============================================================================
    @Override
    public void setupDefaultHandlers() {
        //this.handlers.put(XUnitConfigOpts.ARCH, )
    }

    @Override
    public void addHandler(String name, Setter<String> setter) {

    }

    @Override
    public Setter<String> dispatch(String key) {
        return null;
    }


    public static void main(String[] args) throws IOException {
        File path = new File("/home/stoner/Projects/testpolarize/testing-polarize.yaml");
        FullConfig cfg = Serializer.fromYaml(FullConfig.class, path);
        String selName = cfg.getXunit().getSelector().getName();
    }
}
