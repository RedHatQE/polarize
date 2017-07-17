package com.github.redhatqe.polarize.configuration;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.redhatqe.byzantine.configuration.IConfig;
import com.github.redhatqe.byzantine.configuration.Serializer;
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


public class PolarizeConfig extends ReporterConfig implements IConfig {
    // =========================================================================
    // 1. Add all properties for your class/configuration
    // =========================================================================
    @JsonProperty
    private TestCaseInfo testcase;

    // ==========================================================================
    // 2. Add all fields not belonging to the configuration here
    // ==========================================================================
    @JsonIgnore
    public Map<String, Setter<String>> handlers = new HashMap<>();
    @JsonIgnore
    Testsuites suites;

    // =========================================================================
    // 3. Constructors go here.  Remember that there must be a no-arg constructor
    // =========================================================================
    public PolarizeConfig() {
        super();
    }

    public PolarizeConfig(PolarizeConfig cfg) {
        super(cfg);
    }

    //=============================================================================
    // 4. Define the bean setters and getters for all fields in #1
    //=============================================================================
    public TestCaseInfo getTestcase() {
        if (this.testcase == null)
            this.testcase = new TestCaseInfo();
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
        super.setupDefaultHandlers();

        TestCaseInfo tci = this.getTestcase();
        this.addHandler(PolarizeConfigOpts.TC_SELECTOR_NAME.getOption(),
                (s) -> tci.getSelector().setName(s),
                this.sHandlers);
        this.addHandler(PolarizeConfigOpts.TC_SELECTOR_VAL.getOption(),
                (s) -> tci.getSelector().setValue(s),
                this.sHandlers);
        this.addHandler(PolarizeConfigOpts.TESTCASE_PREFIX.getOption(),
                tci::setSuffix,
                this.sHandlers);
        this.addHandler(PolarizeConfigOpts.TESTCASE_PREFIX.getOption(),
                tci::setPrefix,
                this.sHandlers);
        this.addHandler(PolarizeConfigOpts.TC_IMPORTER_ENABLED.getOption(),
                tci::setEnabled,
                this.bHandlers);
        this.addHandler(PolarizeConfigOpts.TC_IMPORTER_TIMEOUT.getOption(),
                tci::setTimeout,
                this.iHandlers);
    }


    public static void main(String[] args) throws IOException {
        File path = new File("/home/stoner/Projects/testpolarize/testing-polarize.yaml");
        PolarizeConfig cfg = Serializer.fromYaml(PolarizeConfig.class, path);
        String selName = cfg.getXunit().getSelector().getName();
    }
}
