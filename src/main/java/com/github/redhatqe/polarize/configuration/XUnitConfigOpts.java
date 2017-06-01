package com.github.redhatqe.polarize.configuration;

import com.github.redhatqe.byzantine.config.Opts;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * These are the CLI options that can be used
 */
public class XUnitConfigOpts extends Opts {
    public static final String TESTRUN_TITLE = "testrun-title";
    public static final String TESTRUN_ID = "testrun-id";
    public static final String TESTRUN_TYPE = "testrun-type";
    public static final String TESTRUN_TEMPLATE_ID = "testrun-template-id";
    public static final String TESTRUN_GROUP_ID = "testrun-group-id";

    public static final String PROJECT = "project";
    public static final String TESTCASE_PREFIX = "testcase-prefix";
    public static final String TESTCASE_SUFFIX = "testcase-suffix";

    public static final String PLANNEDIN = "plannedin";
    public static final String JENKINSJOBS = "jenkinsjobs";
    public static final String NOTES = "notes";
    public static final String ARCH = "arch";
    public static final String VARIANT = "variant";

    public static final String TC_SELECTOR_NAME = "testcase-selector-name";
    public static final String TC_SELECTOR_VAL = "testcase-selector-val";
    public static final String XUNIT_SELECTOR_NAME = "xunit-selector-name";
    public static final String XUNIT_SELECTOR_VAL = "xunit-selector-val";

    public static final String TC_IMPORTER_ENABLED = "testcase-importer-enabled";
    public static final String XUNIT_IMPORTER_ENABLED = "xunit-importer-enabled";
    public static final String TR_DRY_RUN = "dry-run";
    public static final String TR_SET_FINISHED = "set-testrun-finished";
    public static final String TR_INCLUDE_SKIPPED = "include-skipped";

    public static final String TC_IMPORTER_TIMEOUT = "testcase-importer-timeout";
    public static final String XUNIT_IMPORTER_TIMEOUT = "xunit-importer-timeout";
    public static final String TR_PROPERTY = "property";
    public static final String NEW_XUNIT = "new-xunit";
    public static final String CURRENT_XUNIT = "current-xunit";
    public static final String EDIT_CONFIG = "edit-config";
    public static final String PROJECT_NAME = "project-name";

    public static final String SERVER = "server";
    public static final String BASE_DIR = "base-dir";
    public static final String MAPPING = "mapping";
    public static final String TC_XML_PATH = "testcases-xml";
    public static final String REQ_XML_PATH = "requirements-xml";
    public static final String USERNAME = "user-name";
    public static final String USERPASSWORD = "user-password";
    public static final String HELP = "help";

    public static Set<String> propertyArgs = new HashSet<>();

    static {
        propertyArgs.addAll(Arrays.asList(SERVER, ARCH, VARIANT, NOTES, PLANNEDIN, JENKINSJOBS));
    }
}
