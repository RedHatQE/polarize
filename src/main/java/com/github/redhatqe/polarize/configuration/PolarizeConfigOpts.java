package com.github.redhatqe.polarize.configuration;


import com.github.redhatqe.polarize.reporter.configuration.IGetOpts;

/**
 * These are the CLI options that can be used
 */
public enum PolarizeConfigOpts implements IGetOpts {
    TESTCASE_PREFIX("testcase-prefix", "An optional string which will be prepended to the Testcase title.  Relevant " +
            "to polarize-config"),
    TESTCASE_SUFFIX("testcase-suffix", "An optional string will be appended to the Testcase title.  Relevant to" +
            " polarize-config"),
    TC_SELECTOR_NAME("testcase-selector-name", "A JMS selector is used to filter results.  A selector looks like " +
            "name='val'.  This switch provides the name part of the selector.  Applies to the polarize-config file " +
            "when running a TestCase Import request"),
    TC_SELECTOR_VAL("testcase-selector-val", "As above, but it provides the val in name='val'.  Applies to the " +
            "polarize-config file when running a TestCase Import request"),
    TC_IMPORTER_ENABLED("testcase-importer-enabled", "Whether the TestCase Importer will be enabled or not. If false," +
            " even if polarize detects that a new Polarion TestCase should be created, it will not make the import."),
    TC_IMPORTER_TIMEOUT("testcase-importer-timeout", "The time in miliseconds to wait for a message reply when " +
            "performing a TestCase Import request.  Relevant to polarize-config"),
    HELP("help", "Prints help for all the options");

    private final String option;
    private final String description;
    PolarizeConfigOpts(String opt, String desc) {
        this.option = opt;
        this.description = desc;
    }

    public String getOption() { return this.option; }
    public String getDesc() { return this.description; }

}
