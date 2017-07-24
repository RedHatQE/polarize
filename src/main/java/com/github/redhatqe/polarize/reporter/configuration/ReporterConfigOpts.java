package com.github.redhatqe.polarize.reporter.configuration;


import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public enum ReporterConfigOpts implements IGetOpts {
    TESTRUN_TITLE("testrun-title", "A (possibly non-unique) title to give for a TestRun.  If empty defaults to a " +
            "timestamp. Relevant for xunit file"),
    TESTRUN_ID("testrun-id", "If given, must be a unique ID for the TestRun, otherwise Polarion generates one." +
            "Relevant for xunit file"),
    TESTRUN_TYPE("testrun-type", "The type of test. One of build_acceptance, regression or feature_verification " +
            "Defaults to feature_verification"),
    TESTRUN_TEMPLATE_ID("testrun-template-id", "The string of a template id.  For example, " +
            "--template-id=\"testing template\""),
    TESTRUN_GROUP_ID("testrun-group-id", "The value for the group id (not yet implemented)"),
    PROJECT("project", "Sets the Polarion Project ID.  Relevant for polarize-config or xunit file"),
    PLANNEDIN("plannedin", "PROPERTY: A string representing what plan time this test is for. Relevant to xunit.  " +
            "It is used like this --property plannedin=7_4_Pre-testing "),
    JENKINSJOBS("jenkinsjobs", "PROPERTY: An optional custom field for the jenkins job URL. Relevant to xunit. It is " +
            "used like this: --property jenkinsjobs=$JENKINS_JOB."),
    NOTES("notes", "PROPERTY: An optional free form section for notes.  Relevant to xunit.  It is used " +
            "like this: --property notes=\"Some description\""),
    ARCH("arch", "PROPERTY: Optional arch test was run on. Relevant to xunit.  It is used like this: " +
            "--property arch=x8664"),
    VARIANT("variant", "PROPERTY: Optional variant type like Server or Workstation.  Relevant to xunit.  It is " +
            "used like this: --property variant=Server"),
    XUNIT_SELECTOR_NAME("xunit-selector-name", "As TC_SELECTOR_NAME, but applicable to the xunit file when running an " +
            "XUnit Import request"),
    XUNIT_SELECTOR_VAL("xunit-selector-val", "As TC_SELECTOR_VAL but applicable to the xunit file when running an " +
            "XUnit Import request"),
    TC_IMPORTER_ENABLED("testcase-importer-enabled", "Whether the TestCase Importer will be enabled or not. If false," +
            " even if polarize detects that a new Polarion TestCase should be created, it will not make the import."),
    XUNIT_IMPORTER_ENABLED("xunit-importer-enabled", "Whether the XUnit Importer is enabled or not.  XUnit Importer " +
            "can still be run manually but this setting will be checked for automation"),
    TR_DRY_RUN("dry-run", "When making an XUnit Import request, if set to true, it will not actually create a new " +
            "TestRun, but will only report what it would have created.  Relevant to xunit"),
    TR_SET_FINISHED("set-testrun-finished", "When making an XUnit Import request, if set to true, mark the newly " +
            "created TestRun as finished.  Relevant to xunit"),
    TR_INCLUDE_SKIPPED("include-skipped", "When making an XUnit Import request, if set to true, also include any " +
            "testcases that were marked as skipped in the xunit file (these will show as Blocking in Polarion." +
            "Relevant to xunit"),
    EDIT_CONFIG("edit-configuration", "When set to true, only sets values to the polarize-config file (given as the first " +
            "argument to the CLI). If false, then only read in the xunit file as given by --current-xunit, and create" +
            " a new modified version given the other CLI switches that will be written to --new-xunit"),
    XUNIT_IMPORTER_TIMEOUT("xunit-importer-timeout", "The time in miliseconds to wait for a message reply when " +
            "performing an XUnit Import request.  Relevant to xunit"),
    TR_PROPERTY("property", "Not used directly"),
    NEW_XUNIT("new-xunit", "A path for where the modified xunit file will be written.  Applicable to xunit file"),
    CURRENT_XUNIT("current-xunit", "The path for where to read in the xunit file that will be used as a base"),
    PROJECT_NAME("project-name", "Not Used"),
    SERVER("server", "Takes a comma separated value of server-name,user-name,user-pw,[url].  The last field is " +
            "optional.  Can be called multiple times.  Eg --server polarion,stoner,mypass"),
    BASE_DIR("base-dir", "The absolute path, to where your project directory is.  The value here will replace " +
            "wherever {BASEDIR} is in the polarize-config file.  Relevant to polarize-config"),
    MAPPING("mapping", "Absolute path to where the JSON file that mps methods to IDs will be looked up. Relevant to" +
            " both"),
    USERNAME("user-name", "Set the user name which will be used as the author of TestRuns or TestCases"),
    USERPASSWORD("user-password", "Set the password for the user who will be used as the author of TestRuns or TestCases"),
    CONFIG("configuration", "Path of the polarize-config file to use"),
    HELP("help", "Prints help for all the options");

    private final String option;
    private final String description;

    ReporterConfigOpts(String opt, String desc) {
        this.option = opt;
        this.description = desc;
    }

    @Override
    public String getOption() {
        return this.option;
    }

    @Override
    public String getDesc() {
        return this.description;
    }

    /**
     * These are options that are like: --property arch=x8664
     *
     * @return
     */
    public static Set<ReporterConfigOpts> propertyArgs() {
        Set<ReporterConfigOpts> props = new HashSet<>();
        ReporterConfigOpts[] props_ = { SERVER, ARCH, VARIANT, NOTES, PLANNEDIN, JENKINSJOBS };
        props.addAll(Arrays.asList(props_));
        return props;
    }

    public static Set<ReporterConfigOpts> sOpts() {
        Set<ReporterConfigOpts> opts = new HashSet<>();
        ReporterConfigOpts[] opts_ = { TESTRUN_TITLE, TESTRUN_ID, TESTRUN_TYPE, TESTRUN_TEMPLATE_ID, TESTRUN_GROUP_ID,
                PROJECT, PLANNEDIN, JENKINSJOBS, NOTES, ARCH, VARIANT, XUNIT_SELECTOR_NAME, XUNIT_SELECTOR_VAL,
                NEW_XUNIT, CURRENT_XUNIT, PROJECT_NAME, BASE_DIR, MAPPING, USERNAME, USERPASSWORD };
        opts.addAll(Arrays.asList(opts_));
        return opts;
    }

    public static Set<ReporterConfigOpts> bOpts() {
        Set<ReporterConfigOpts> opts = new HashSet<>();
        ReporterConfigOpts[] opts_ = { TC_IMPORTER_ENABLED, XUNIT_IMPORTER_ENABLED, TR_DRY_RUN, TR_INCLUDE_SKIPPED,
                TR_SET_FINISHED, EDIT_CONFIG };
        opts.addAll(Arrays.asList(opts_));
        return opts;
    }

    public static Set<ReporterConfigOpts> iOpts() {
        Set<ReporterConfigOpts> opts = new HashSet<>();
        ReporterConfigOpts[] opts_ = {XUNIT_IMPORTER_TIMEOUT};
        opts.addAll(Arrays.asList(opts_));
        return opts;
    }
}
