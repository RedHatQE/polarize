package com.github.redhatqe.polarize.configuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by stoner on 11/7/16.
 */
public class Opts {
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

    public static final String fmt = "--%s %s";

    private String baseDir;
    private String author;
    private String mapping;
    private String tcXML;
    private String project;
    private String projectName;
    private String groupId;
    private Boolean editConfig;

    private TestCaseImporter tci;
    private XUnit xunit;
    private TestRun testrun;
    private Prop[] properties;
    private List<Server> servers;

    @JsonProperty(value="base-dir")
    public String getBaseDir() {
        return baseDir;
    }

    @JsonProperty(value="base-dir")
    public void setBaseDir(String baseDir) {
        this.baseDir = baseDir;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getMapping() {
        return mapping;
    }

    public void setMapping(String mapping) {
        this.mapping = mapping;
    }

    @JsonProperty(value="testcases-xml")
    public String getTcXML() {
        return tcXML;
    }

    @JsonProperty(value="testcases-xml")
    public void setTcXML(String tcXML) {
        this.tcXML = tcXML;
    }

    public String getProject() {
        return project;
    }

    public void setProject(String project) {
        this.project = project;
    }

    @JsonProperty(value="project-name")
    public String getProjectName() {
        return projectName;
    }

    @JsonProperty(value="project-name")
    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    @JsonProperty(value="group-id")
    public String getGroupId() {
        return groupId;
    }

    @JsonProperty(value="group-id")
    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    @JsonProperty(value="edit-config")
    public Boolean getEditConfig() {
        return editConfig;
    }

    @JsonProperty(value="edit-config")
    public void setEditConfig(Boolean editConfig) {
        this.editConfig = editConfig;
    }

    @JsonProperty(value="testcase")
    public TestCaseImporter getTci() {
        return tci;
    }

    @JsonProperty(value="testcase")
    public void setTci(TestCaseImporter tci) {
        this.tci = tci;
    }

    @JsonProperty(value="xunit")
    public XUnit getXunit() {
        return xunit;
    }

    @JsonProperty(value="xunit")
    public void setXunit(XUnit xunit) {
        this.xunit = xunit;
    }

    public TestRun getTestrun() {
        return testrun;
    }

    public void setTestrun(TestRun testrun) {
        this.testrun = testrun;
    }

    public Prop[] getProperties() {
        return properties;
    }

    public void setProperties(Prop[] properties) {
        this.properties = properties;
    }

    public List<Server> getServers() {
        return servers;
    }

    public void setServers(List<Server> servers) {
        this.servers = servers;
    }


    public interface IArgList {
        default List<String> argList(Map<String, String> m) {
                return m.entrySet().stream()
                        .flatMap(es -> {
                            String opt = es.getKey();
                            String val = es.getValue();
                            if (val.contains(" "))
                                val = String.format("\"%s\"", val);
                            List<String> args = new ArrayList<>();
                            args.add(String.format("--%s", opt));
                            args.add(val);
                            return args.stream();
                        })
                        .collect(Collectors.toList());
        }
    }

    public static class Importer implements IArgList {
        protected Boolean enabled;
        protected Integer timeout;
        protected String file;
        protected String selectorName;
        protected String selectorVal;
        public Map<String, String> argMap = new HashMap<>();
        public List<String> args = new ArrayList<>();

        public Importer() {

        }

        public String getFile() {
            return file;
        }

        public void setFile(String file) {
            this.file = file;
        }

        /**
         * Subclasses of Importer will define the equivalent XML CLI arg to change an attribute.
         *
         * For example {"prefix": "--testcase-prefix"} means that the prefix attribute maps to --testcase-prefix
         * @return
         */
        public Map<String, String> createArgMap() {
            return this.argMap;
        }
    }

    public static class XUnit extends Importer {
        private String newXunit;  // path to temp Xunit result

        @JsonCreator
        public XUnit(
                @JsonProperty("new") String newXml,
                @JsonProperty("importer-enabled") Boolean enabled,
                @JsonProperty("timeout") Integer timeout,
                @JsonProperty("file") String file,
                @JsonProperty("selector-name") String selName,
                @JsonProperty("selector-val") String selVal) {
            this.newXunit = newXml;
            this.enabled = enabled;
            this.timeout = timeout;
            this.file = file;
            this.selectorName = selName;
            this.selectorVal = selVal;
        }

        @JsonProperty(value="new")
        public void setNewXunit(String newXunit) {
            argMap.put(Opts.NEW_XUNIT, newXunit);
            this.newXunit = newXunit;
        }

        @JsonProperty(value="new")
        public String getNewXunit() {
            return this.newXunit;
        }

        @JsonProperty(value="importer-enabled")
        public void setEnabled(Boolean enabled) {
            argMap.put(Opts.XUNIT_IMPORTER_ENABLED, Boolean.toString(enabled));
            this.enabled = enabled;
        }

        @JsonProperty(value="importer-enabled")
        public Boolean getEnabled() {
            return this.enabled;
        }

        @JsonProperty(value="timeout")
        public void setTimeout(Integer timeout) {
            argMap.put(Opts.XUNIT_IMPORTER_TIMEOUT, Integer.toString(timeout));
            this.timeout = timeout;
        }

        @JsonProperty(value="timeout")
        public Integer getTimeout(){
            return this.timeout;
        }

        @JsonProperty(value="selector-name")
        public void setSelectorName(String name) {
            argMap.put(Opts.XUNIT_SELECTOR_NAME, name);
            this.selectorName = name;
        }

        @JsonProperty(value="selector-name")
        public String getSelectorName() {
            return this.selectorName;
        }

        @JsonProperty(value="selector-val")
        public void setSelectorVal(String selectorVal) {
            argMap.put(Opts.XUNIT_SELECTOR_VAL, selectorVal);
            this.selectorVal = selectorVal;
        }

        @JsonProperty(value="selector-val")
        public String getSelectorVal() {
            return this.selectorVal;
        }
    }

    public static class TestCaseImporter extends Importer {
        private String prefix;
        private String suffix;

        @JsonCreator
        public TestCaseImporter(
                @JsonProperty("prefix") String prefix,
                @JsonProperty("suffix") String suffix,
                @JsonProperty("importer-enabled") Boolean enabled,
                @JsonProperty("timeout") Integer timeout,
                @JsonProperty("file") String file,
                @JsonProperty("selector-name") String selName,
                @JsonProperty("selector-val") String selVal) {
            super();
            this.enabled = enabled;
            this.timeout = timeout;
            this.file = file;
            this.selectorName = selName;
            this.selectorVal = selVal;
        }

        @JsonProperty("prefix")
        public void setPrefix(String prefix) {
            if (!prefix.equals(""))
                argMap.put(Opts.TESTCASE_PREFIX, prefix);
            this.prefix = prefix;
        }

        @JsonProperty("suffix")
        public void setSuffix(String suffix) {
            if (!suffix.equals(""))
                argMap.put(Opts.TESTCASE_SUFFIX, suffix);
            this.suffix = suffix;
        }

        @JsonProperty("selector-name")
        public void setSelectorName(String name) {
            if (!name.equals(""))
                argMap.put(Opts.TC_SELECTOR_NAME, name);
            this.selectorName = name;
        }

        @JsonProperty("selector-name")
        public String getSelectorName() {
            return this.selectorName;
        }

        @JsonProperty("selector-val")
        public void setSelectorVal(String selectorVal) {
            if (!selectorVal.equals(""))
                argMap.put(Opts.TC_SELECTOR_VAL, selectorVal);
            this.selectorVal = selectorVal;
        }

        @JsonProperty("selector-val")
        public String getSelectorVal() {
            return this.selectorVal;
        }

        @JsonProperty("timeout")
        public void setTimeout(Integer timeout) {
            argMap.put(Opts.TC_IMPORTER_TIMEOUT, Integer.toString(timeout));
            this.timeout = timeout;
        }

        @JsonProperty("timeout")
        public Integer getTimeout() {
            return this.timeout;
        }

        @JsonProperty("enabled")
        public void setEnabled(Boolean enabled) {
            argMap.put(Opts.TC_IMPORTER_ENABLED, Boolean.toString(enabled));
            this.enabled = enabled;
        }

        @JsonProperty("enabled")
        public Boolean getEnabled() {
            return this.enabled;
        }
    }

    public static class TestRun implements IArgList {
        private String templateId;
        private String title;
        private String id;
        private Boolean dryRun;
        private Boolean setFinished;
        private Boolean includeSkipped;
        public Map<String, String> argMap = new HashMap<>();
        public List<String> args = new ArrayList<>();

        @JsonCreator
        public TestRun(
                @JsonProperty("template-id") String templateId,
                @JsonProperty("title") String title,
                @JsonProperty("id") String id,
                @JsonProperty("dry-run") Boolean dryRun,
                @JsonProperty("set-finished") Boolean setFinished,
                @JsonProperty("include-skipped") Boolean includeSkipped) {
            this.templateId = templateId;
            this.title = title;
            this.id = id;
            this.dryRun = dryRun;
            this.setFinished = setFinished;
            this.includeSkipped = includeSkipped;
        }

        @JsonProperty("template-id")
        public void setTemplateId(String templateId) {
            if (!templateId.equals(""))
                argMap.put(Opts.TESTRUN_TEMPLATE_ID, templateId);
            this.templateId = templateId;
        }

        @JsonProperty("template-id")
        public String getTemplateId() {
            return this.templateId;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            if (!title.equals(""))
                argMap.put(Opts.TESTRUN_TITLE, title);
            this.title = title;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            if (!id.equals(""))
                argMap.put(Opts.TESTRUN_ID, id);
            this.id = id;
        }

        @JsonProperty("dry-run")
        public void setDryRun(Boolean dryRun) {
            argMap.put(Opts.TR_DRY_RUN, Boolean.toString(dryRun));
            this.dryRun = dryRun;
        }

        @JsonProperty("dry-run")
        public Boolean getDryRun() {
            return this.dryRun;
        }

        @JsonProperty("set-finished")
        public void setSetFinished(Boolean setFinished) {
            argMap.put(Opts.TR_SET_FINISHED, Boolean.toString(setFinished));
            this.setFinished = setFinished;
        }

        @JsonProperty("set-finished")
        public Boolean getSetFinished() {
            return this.setFinished;
        }

        @JsonProperty("include-skipped")
        public void setIncludeSkipped(Boolean includeSkipped) {
            argMap.put(Opts.TR_INCLUDE_SKIPPED, Boolean.toString(includeSkipped));
            this.includeSkipped = includeSkipped;
        }

        @JsonProperty("include-skipped")
        public Boolean getIncludeSkipped() {
            return this.includeSkipped;
        }
    }

    public static class Prop {
        public String name;
        public String val;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getVal() {
            return val;
        }

        public void setVal(String val) {
            this.val = val;
        }
    }


    public static class Properties {
        public List<Prop> properties;

        public Properties() {

        }

        public List<Prop> getProperties() {
            return properties;
        }

        public void setProperties(List<Prop> properties) {
            this.properties = properties;
        }
    }


    public static class Server {
        private String name;
        private String user;
        private String password;
        private String url;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
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

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }
    }

    public void addToArgs(List<String> argList, String opt, String val) {
        argList.add(opt);
        argList.add(val);
    }

    /**
     * Used for the REST API.
     *
     * {
     *     "base-dir": "",
     *     "author": "stoner",
     *     "mapping": "{basedir}/mapping.json",
     *     "testcases-xml": "{basedir}/testcases",
     *     "project": "",
     *     "project-name": "",
     *     "group-id": "",
     *     "edit-config: "",
     *     "testcase": {
     *         "prefix": "",
     *         "suffix": "",
     *         "importer-enabled": false,
     *         "timeout": 300000,
     *         "file": "/tmp/testcases.xml",
     *         "selector-name": "name-for-testcase-selector",
     *         "selector-val": "val-for-testcase-selector"
     *     }
     *     xunit: {
     *         "new": "/tmp/modified-testng-polarion.xml",
     *         "file": "/path/to/testng-polarion.xml",
     *         "importer-enabled": true,
     *         "timeout": 3000000,
     *         "selector-name": "name-for-xunit-selector",
     *         "selector-val": "val-for-xunit-selector"
     *     }
     *     testrun: {
     *         "templateId": "sean toner master test template",
     *         "title": "RHSM TestRun for RHEL 7.4 Server x86_64",
     *         "id": "RHSM TestRun for RHEL 7.4 Server x86_64 NOV-7-2016-14-32-10",
     *         "dry-run": false,
     *         "set-finished": true,
     *         "include-skipped": true
     *     }
     *     properties: [
     *         {
     *             "name": "plannedin",
     *             "val": "RHEL_6_9",
     *         },
     *         {
     *             "name": "jenkinsjobs",
     *             "val": "http://path/to/jenkins/job"
     *         }
     *     ]
     *     servers: [
     *         {
     *             "name": "polarion",
     *             "url": "http://path/to/polarion",
     *             "user": "ci-user",
     *             "password": "password"
     *         },
     *         {
     *             "name": "kerb",
     *             "user": "user",
     *             "password": "password"
     *         }
     *     ]
     * }
     *
     * @param
     */
    public String[] parse(String body) {
        System.out.println(body);
        ObjectMapper mapper = new ObjectMapper();

        List<String> args = new ArrayList<>();
        List<String> testcaseArgs = this.tci.argList(this.tci.argMap);
        List<String> xunitArgs = this.xunit.argList(this.xunit.argMap);
        List<String> testrunArgs = this.testrun.argList(this.testrun.argMap);

        args.addAll(testcaseArgs);
        args.addAll(xunitArgs);
        args.addAll(testrunArgs);

        System.out.println(args.stream().reduce("", (acc, n) -> acc + " " + n));
        return args.toArray(new String[args.size()]);
    }

    public Opts() {

    }

    /**
     * Takes an xml-config.xml and converts it to json
     * @param xml
     */
    public void xmlToJson(File xml) {

    }
}
