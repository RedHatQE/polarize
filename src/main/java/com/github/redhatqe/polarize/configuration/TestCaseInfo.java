package com.github.redhatqe.polarize.configuration;


import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.redhatqe.polarize.reporter.configuration.ImporterInfo;

public class TestCaseInfo extends ImporterInfo {
    @JsonProperty
    private Title title;

    public TestCaseInfo() {
        super();
        this.title = new Title();
    }

    public Title getTitle() {
        return title;
    }

    public void setTitle(Title title) {
        this.title = title;
    }

    public String getPrefix() { return this.title.getPrefix(); }
    public void setPrefix(String prefix) { this.title.setPrefix(prefix); }
    public String getSuffix() { return this.title.getSuffix(); }
    public void setSuffix(String suffix) { this.title.setSuffix(suffix); }

    public class Title {
        @JsonProperty
        private String prefix;
        @JsonProperty
        private String suffix;

        public String getPrefix() {
            return prefix;
        }

        public void setPrefix(String prefix) {
            this.prefix = prefix;
        }

        public String getSuffix() {
            return suffix;
        }

        public void setSuffix(String suffix) {
            this.suffix = suffix;
        }
    }


}
