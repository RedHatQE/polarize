package com.github.redhatqe.polarize.configuration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.github.redhatqe.polarize.Utility;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by stoner on 11/7/16.
 */
public class Opts {
    public static final String TESTRUN_TITLE = "testrun-title";
    public static final String TESTRUN_ID = "testrun-id";
    public static final String PROJECT = "project";
    public static final String TESTCASE_PREFIX = "testcase-prefix";
    public static final String TESTCASE_SUFFIX = "testcase-suffix";
    public static final String PLANNEDIN= "plannedin";
    public static final String JENKINSJOBS= "jenkinsjobs";
    public static final String NOTES ="notes";
    public static final String TEMPLATE_ID = "template-id";
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
    public static final String GROUP_ID = "group-id";
    public static final String SERVER = "server";
    public static final String BASE_DIR = "base-dir";
    public static final String MAPPING = "mapping";
    public static final String AUTHOR = "author";

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
    private Props properties;
    private Servers servers;

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

    public class Importer implements IArgList {
        protected Boolean enabled;
        protected Integer timeout;
        protected String file;
        protected String selectorName;
        protected String selectorVal;
        public Map<String, String> argMap = new HashMap<>();
        public List<String> args = new ArrayList<>();

        public Importer() {

        }

        public Boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }

        public Integer getTimeout() {
            return timeout;
        }

        public void setTimeout(Integer timeout) {
            this.timeout = timeout;
        }

        public String getFile() {
            return file;
        }

        public void setFile(String file) {
            this.file = file;
        }

        public String getSelectorName() {
            return selectorName;
        }

        public void setSelectorName(String selectorName) {
            this.selectorName = selectorName;
        }

        public String getSelectorVal() {
            return selectorVal;
        }

        public void setSelectorVal(String selectorVal) {
            this.selectorVal = selectorVal;
        }
    }

    public class XUnit extends Importer {
        private String newXunit;
        public XUnit() {
            super();
        }

        public XUnit(JsonNode node) {
            super();
            Iterator<Map.Entry<String, JsonNode>> iter = node.fields();
            while(iter.hasNext()) {
                Map.Entry<String, JsonNode> me = iter.next();
                String key = me.getKey();
                JsonNode n = me.getValue();
                String val = n.textValue();
                String fmt = "--%s %s";

                switch(key) {
                    case "importer-enabled":
                        this.setEnabled(Boolean.valueOf(val));
                        break;
                    case "file":
                        // TODO: Add way to change the <file path=""> for testcase importer
                        this.setFile(val);
                        break;
                    case "selector-name":
                        this.setSelectorName(val);
                        break;
                    case "selector-val":
                        this.setSelectorVal(val);
                        break;
                    case "timeout":
                        if (n instanceof  IntNode)
                            this.setTimeout(n.intValue());
                        else
                            System.err.println("timeout must evaluate to an integer (no quotes)");
                        break;
                    case "new":
                        this.setNewXunit(val);
                        break;
                }
            }
        }

        public String getNewXunit() {
            return newXunit;
        }

        public void setNewXunit(String newXunit) {
            argMap.put(Opts.NEW_XUNIT, newXunit);
            this.newXunit = newXunit;
        }

        public void setEnabled(Boolean enabled) {
            argMap.put(Opts.XUNIT_IMPORTER_ENABLED, Boolean.toString(enabled));
            this.enabled = enabled;
        }

        public void setTimeout(Integer timeout) {
            argMap.put(Opts.XUNIT_IMPORTER_TIMEOUT, Integer.toString(timeout));
            this.timeout = timeout;
        }

        public void setSelectorName(String name) {
            argMap.put(Opts.XUNIT_SELECTOR_NAME, name);
            this.selectorName = name;
        }

        public void setSelectorVal(String selectorVal) {
            argMap.put(Opts.XUNIT_SELECTOR_VAL, selectorVal);
            this.selectorVal = selectorVal;
        }
    }

    public class TestCaseImporter extends Importer {
        private String prefix;
        private String suffix;

        public TestCaseImporter(JsonNode node) {
            super();
            Iterator<Map.Entry<String, JsonNode>> iter = node.fields();
            while(iter.hasNext()) {
                Map.Entry<String, JsonNode> me = iter.next();
                String key = me.getKey();
                JsonNode n = me.getValue();
                String val = n.textValue();
                String fmt = "--%s %s";

                switch(key) {
                    case "importer-enabled":
                        this.setEnabled(Boolean.valueOf(val));
                        break;
                    case "file":
                        // TODO: Add way to change the <file path=""> for testcase importer
                        this.setFile(val);
                        break;
                    case "selector-name":
                        this.setSelectorName(val);
                        break;
                    case "selector-val":
                        this.setSelectorVal(val);
                        break;
                    case "timeout":
                        if (n instanceof IntNode)
                            this.setTimeout(n.intValue());
                        else
                            System.err.println("timeout must evaluate to a number (no quotes)");
                        break;
                    case "prefix":
                        this.setPrefix(val);
                        break;
                    case "suffix":
                        this.setSuffix(val);
                        break;
                }
            }
        }

        public TestCaseImporter() {
            super();
        }

        public String getPrefix() {
            return prefix;
        }

        public void setPrefix(String prefix) {
            if (!prefix.equals(""))
                argMap.put(Opts.TESTCASE_PREFIX, prefix);
            this.prefix = prefix;
        }

        public String getSuffix() {
            return suffix;
        }

        public void setSuffix(String suffix) {
            if (!suffix.equals(""))
                argMap.put(Opts.TESTCASE_SUFFIX, suffix);
            this.suffix = suffix;
        }

        public void setSelectorName(String name) {
            if (!name.equals(""))
                argMap.put(Opts.TC_SELECTOR_NAME, name);
            this.selectorName = name;
        }

        public void setSelectorVal(String selectorVal) {
            if (!selectorVal.equals(""))
                argMap.put(Opts.TC_SELECTOR_VAL, selectorVal);
            this.selectorVal = selectorVal;
        }

        public void setTimeout(Integer timeout) {
            argMap.put(Opts.TC_IMPORTER_TIMEOUT, Integer.toString(timeout));
            this.timeout = timeout;
        }

        public void setEnabled(Boolean enabled) {
            argMap.put(Opts.TC_IMPORTER_ENABLED, Boolean.toString(enabled));
            this.enabled = enabled;
        }
    }

    public class TestRun implements IArgList{
        private String templateId;
        private String title;
        private String id;
        private Boolean dryRun;
        private Boolean setFinished;
        private Boolean includeSkipped;
        public Map<String, String> argMap = new HashMap<>();
        public List<String> args = new ArrayList<>();

        public TestRun(JsonNode node) {
            Iterator<Map.Entry<String, JsonNode>> iter = node.fields();
            while(iter.hasNext()) {
                Map.Entry<String, JsonNode> me = iter.next();
                String key = me.getKey();
                JsonNode n = me.getValue();
                String val = n.textValue();
                String fmt = "--%s %s";

                switch(key) {
                    case "template-id":
                        this.setTemplateId(val);
                        break;
                    case "title":
                        this.setTitle(val);
                        break;
                    case "id":
                        this.setId(val);
                        break;
                    case "dry-run":
                        if (n instanceof BooleanNode)
                            this.setDryRun(n.booleanValue());
                        else
                            System.err.println("dry-run must be a boolean value (no quotes)");
                        break;
                    case "set-finished":
                        if (n instanceof BooleanNode)
                            this.setSetFinished(n.booleanValue());
                        else
                            System.err.println("set-finished must be a boolean value (no quotes)");
                        break;
                    case "include-skipped":
                        if (n instanceof BooleanNode)
                            this.setIncludeSkipped(n.booleanValue());
                        else
                            System.err.println("include-skipped must be a boolean value (no quotes)");
                        break;
                    default:
                        System.out.println("Skipping key of " + key);
                }
            }
        }

        public String getTemplateId() {
            return templateId;
        }

        public void setTemplateId(String templateId) {
            if (!templateId.equals(""))
                argMap.put(Opts.TEMPLATE_ID, templateId);
            this.templateId = templateId;
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

        public Boolean getDryRun() {
            return dryRun;
        }

        public void setDryRun(Boolean dryRun) {
            argMap.put(Opts.TR_DRY_RUN, Boolean.toString(dryRun));
            this.dryRun = dryRun;
        }

        public Boolean getSetFinished() {
            return setFinished;
        }

        public void setSetFinished(Boolean setFinished) {
            argMap.put(Opts.TR_SET_FINISHED, Boolean.toString(setFinished));
            this.setFinished = setFinished;
        }

        public Boolean getIncludeSkipped() {
            return includeSkipped;
        }

        public void setIncludeSkipped(Boolean includeSkipped) {
            argMap.put(Opts.TR_INCLUDE_SKIPPED, Boolean.toString(includeSkipped));
            this.includeSkipped = includeSkipped;
        }
    }

    public class Props {
        public List<String> args = new ArrayList<>();

        public Props(JsonNode node) {
            Iterator<Map.Entry<String, JsonNode>> iter = node.fields();
            String fmt = "--property";

            node.forEach(n -> {
                String propName = "";
                String propVal = "";
                Iterator<Map.Entry<String, JsonNode>> inner = n.fields();
                while(inner.hasNext()) {
                    Map.Entry<String, JsonNode> e = inner.next();
                    String pname = e.getKey();
                    String pval = e.getValue().textValue();

                    switch (pname) {
                        case "name":
                            propName = pval;
                            break;
                        case "val":
                            propVal = pval;
                            if (pval.contains(" "))
                                propVal = String.format("\"%s\"", pval);
                            break;
                    }
                }
                String property = String.format("%s=%s", propName, propVal);
                args.add(fmt);
                args.add(property);
            });
        }
    }


    class Servers {
        public List<String> args = new ArrayList<>();

        // This option takes the form of name,user,pw,url.  If any are missing, leave it empty. name is required
        // --server polarion,ci-user,&$Err,http://some/url
        // --server kerb,stoner,myP@ss,
        // --server ossrh,stoner,ossrh-p@ss,
        public Servers(JsonNode node) {
            String fmt = "--server";

            node.forEach(n -> {
                Iterator<Map.Entry<String, JsonNode>> inner = n.fields();
                String value = "";
                while(inner.hasNext()) {
                    Map.Entry<String, JsonNode> e = inner.next();
                    String val = e.getValue().textValue();
                    value += val + ",";
                }
                value = Utility.removeLast(value);
                args.add(fmt);
                args.add(value);
            });
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
     * @param body
     */
    public String[] parse(String body) {
        System.out.println(body);
        ObjectMapper mapper = new ObjectMapper();
        List<String> args = new ArrayList<>();
        try {
            JsonNode root = mapper.readTree(body);
            Iterator<Map.Entry<String, JsonNode>> iter = root.fields();
            while(iter.hasNext()) {
                Map.Entry<String, JsonNode> n = iter.next();
                String key = n.getKey();
                JsonNode node = n.getValue();
                String val = node.textValue();
                String fmt = "--%s %s";

                switch(key) {
                    case "project":
                        if (!val.equals(""))
                            this.addToArgs(args, Opts.PROJECT, val);
                        this.project = val;
                        break;
                    case "base-dir":
                        // TODO: Add base-dir for configuration
                        break;
                    case "author":
                        // TODO: add author for configuration
                        System.out.println("Need to add author as configurable");
                        break;
                    case "mapping":
                        // TODO: add mapping for configuration
                        System.out.println("Need to add mapping as a configurable settings");
                        break;
                    case "project-name":
                        if (!val.equals(""))
                            this.addToArgs(args, Opts.PROJECT_NAME, val);
                        break;
                    case "group-id":
                        if (!val.equals(""))
                            this.addToArgs(args, Opts.GROUP_ID, val);
                        break;
                    case "edit-config":
                        if (!val.equals(""))
                            this.addToArgs(args, Opts.EDIT_CONFIG, val);
                        break;
                    case "testcase":
                        this.tci = new TestCaseImporter(node);
                        break;
                    case "xunit":
                        this.xunit = new XUnit(node);
                        break;
                    case "testrun":
                        this.testrun = new TestRun(node);
                        break;
                    case "properties":
                        this.properties = new Props(node);
                        break;
                    case "servers":
                        this.servers = new Servers(node);
                        break;
                    default:
                        System.out.println("Ignoring key of " + key);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        List<String> testcaseArgs = this.tci.argList(this.tci.argMap);
        List<String> xunitArgs = this.xunit.argList(this.xunit.argMap);
        List<String> testrunArgs = this.testrun.argList(this.testrun.argMap);

        args.addAll(testcaseArgs);
        args.addAll(xunitArgs);
        args.addAll(testrunArgs);
        args.addAll(this.properties.args);
        args.addAll(this.servers.args);

        System.out.println(args.stream().reduce("", (acc, n) -> acc + " " + n));
        return args.toArray(new String[args.size()]);
    }

    public Opts() {

    }
}
