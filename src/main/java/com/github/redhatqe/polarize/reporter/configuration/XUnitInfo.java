package com.github.redhatqe.polarize.reporter.configuration;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.redhatqe.polarize.exceptions.InvalidArgument;

import java.util.*;

public class XUnitInfo extends ImporterInfo {
    @JsonProperty
    private Custom custom;
    @JsonProperty
    private Testrun testrun;

    public XUnitInfo() {
        super();
        this.custom = new Custom();
    }

    public XUnitInfo copy() {
        XUnitInfo xi = new XUnitInfo();
        Map<String, Boolean> testSuite = new HashMap<>();
        Map<String, String> props = new HashMap<>();
        this.custom.getTestSuite().forEach(testSuite::put);
        this.custom.getProperties().forEach(props::put);
        xi.custom.setTestSuite(testSuite);
        xi.custom.setProperties(props);
        xi.testrun = this.testrun.copy();

        xi.setTimeout(this.getTimeout());
        xi.setEnabled(this.getEnabled());
        Selector sel = new Selector();
        sel.setValue(this.getSelector().getValue());
        sel.setName(this.getSelector().getName());
        xi.setSelector(sel);
        xi.setEndpoint(this.getEndpoint());
        return xi;
    }

    public class Custom {
        @JsonProperty(required=true)
        private Map<String, String> properties;
        @JsonProperty("test-suite")
        private Map<String, Boolean> testSuite;

        public Custom() {

        }

        public Map<String, String> getProperties() {
            return properties;
        }

        public void setProperties(Map<String, String> properties) {
            this.properties = properties;
        }

        public Map<String, Boolean> getTestSuite() {
            return testSuite;
        }

        public void setTestSuite(Map<String, Boolean> testSuite) {
            this.testSuite = testSuite;
        }
    }

    public class Testrun {
        @JsonProperty
        private String id;
        @JsonProperty(required=true)
        private String title;
        @JsonProperty(value="template-id", required=true)
        private String templateId;
        @JsonProperty(value="group-id")
        private String groupId;
        @JsonProperty(value="type")
        private String type;

        public Testrun() {

        }

        public Testrun copy() {
            Testrun tr = new Testrun();
            tr.groupId = this.groupId;
            tr.id = this.id;
            tr.title = this.title;
            tr.templateId = this.templateId;
            tr.type = this.type;
            return tr;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getTemplateId() {
            return templateId;
        }

        public void setTemplateId(String templateId) {
            this.templateId = templateId;
        }

        public String getGroupId() { return this.groupId; }

        public void setGroupId(String groupId) { this.groupId = groupId; }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            String[] allowed = {"regression", "buildacceptance", "featureacceptance"};
            List<String> check = Arrays.asList(allowed);
            Set<String> allowed_ = new HashSet<>(check);
            if (!allowed_.contains(type))
                throw new InvalidArgument("Testrun type must be one of " + String.join(",", allowed));
            this.type = type;
        }
    }

    public Custom getCustom() {
        return custom;
    }

    public void setCustom(Custom custom) {
        this.custom = custom;
    }

    public Testrun getTestrun() {
        return testrun;
    }

    public void setTestrun(Testrun testrun) {
        this.testrun = testrun;
    }
}
