package com.github.redhatqe.polarize.configuration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.github.redhatqe.polarize.junitreporter.ReporterConfig;
import com.github.redhatqe.polarize.metadata.DefTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Created by stoner on 10/13/16.
 */
public class YAMLConfig {
    public Server polarionServer;
    public Server kerbServer;
    public Server ossrhServer;
    public String testcasesXMLPath;
    public String requirementsXMLPath;
    public String mappingPath;
    public TestCaseImporter tcImporter;
    public XUnitImporter xunitImporter;
    public String author;
    public String project;
    public Logger logger;

    public Boolean setDryRun;

    public YAMLConfig() {
        this.tcImporter = new TestCaseImporter();
        this.xunitImporter = new XUnitImporter();
        this.logger = LoggerFactory.getLogger(YAMLConfig.class);
    }

    class Server {
        public String url = "";
        public String user;
        public String password;
    }

    abstract class Importer {
        public String author;
        public DefTypes.Project project;
        public String endpoint;
        public String selector;
        public String selectorName;
        public String selectorValue;
        public String file;
        public Integer timeout;
    }

    class XUnitImporter extends Importer {
        Map<String, String> testsuite = new HashMap<>();
        // TODO:  Try to find all valid custom fields and make this an enumeration
        Map<String, String> properties = new HashMap<>();
        public String testrunId;
        public String testrunTitle;
    }

    class TestCaseImporter extends Importer {
        public String testrunPrefix;
        public String testrunSuffix;
    }

    public void parse(JsonNode root) {
        root.fields().forEachRemaining(entry -> {
            String key = entry.getKey();
            JsonNode value = entry.getValue();
            switch(key) {
                case "polarion":
                case "kerberos":
                case "ossrh":
                    this.serverHandler(key, value);
                    break;
                case "paths":
                    this.pathsParser(value);
                    break;
                case "importer":
                    this.importerHandler(value, null);
                    break;
                default:
                    this.logger.error("Unknown root key: %s", key);
            }
        });
    }

    private void serverHandler(String key, JsonNode node) {
        Server server = new Server();
        node.fields().forEachRemaining(n -> {
            String name = n.getKey();
            switch(name) {
                case "url":
                    server.url = n.getValue().asText();
                    break;
                case "password":
                    server.password = n.getValue().asText();
                    break;
                case "user":
                    server.user = n.getValue().asText();
                    break;
                default:
                    throw new Error("%s is in invalid.  Must be one of [url, password, user]");
            }
        });
        switch(key) {
            case "polarion":
                this.polarionServer = server;
                break;
            case "ossrh":
                this.ossrhServer = server;
                break;
            case "kerberos":
                this.kerbServer = server;
                break;
            default:
                throw new Error("key must be one of 'polarion', 'ossrh', or 'kerberos'");
        }
    }

    private void importerHandler(JsonNode root, Importer importer) {
        root.fields().forEachRemaining(entry -> {
            String key = entry.getKey();
            JsonNode value = entry.getValue();
            switch(key) {
                case "author":
                    author = value.asText();
                    break;
                case "project":
                    project = value.asText();
                    break;
                case "testcase":
                    TestCaseImporter imp = this.tcImporter;
                    imp.author = author;
                    imp.project = DefTypes.Project.valueOf(project);
                    this.importerHandler(value, imp);
                case "xunit":
                    XUnitImporter ximp = this.xunitImporter;
                    ximp.author = author;
                    ximp.project = DefTypes.Project.valueOf(project);
                    this.importerHandler(value, ximp);
                    break;
                case "endpoint":
                    importer.endpoint = value.textValue();
                    break;
                case "file":
                    importer.file = value.textValue();
                    break;
                case "selector":
                    this.selectorParser(value, importer);
                    break;
                case "title":
                    if (importer instanceof TestCaseImporter)
                        this.titleParser(value, (TestCaseImporter) importer);
                    break;
                case "timeout":
                    importer.timeout = Integer.parseInt(value.toString());
                    break;
                case "testrun":
                    if (importer instanceof XUnitImporter)
                        this.testrunParser(value, (XUnitImporter) importer);
                    break;
                case "custom":
                    if (importer instanceof XUnitImporter)
                        this.customParser(value, (XUnitImporter) importer);
                    break;
                default:
                    this.logger.error(String.format("Unknown key for root: %s", key));
            }
        });
    }

    private void selectorParser(JsonNode node, Importer imp) {
        String name = "";
        String val = "";
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while(fields.hasNext()) {
            Map.Entry<String, JsonNode> n = fields.next();
            if (n.getKey().equals("name"))
                name = n.getValue().asText();
            else if (n.getKey().equals("value"))
                val = n.getValue().asText();
            else
                this.logger.error("Unknown key for selector: %s", n.getKey());
        }
        imp.selectorName = name;
        imp.selectorValue = val;
        imp.selector = String.format("%s='%s'", name, val);
    }

    private void testrunParser(JsonNode node, XUnitImporter ximp) {
        node.fields().forEachRemaining(e -> {
            String key = e.getKey();
            switch(key) {
                case "id":
                    ximp.testrunId = e.getValue().textValue();
                    break;
                case "title":
                    ximp.testrunTitle = e.getValue().textValue();
                    break;
                default:
                    this.logger.error("Unknown key for testrun: %s", key);
            }
        });
    }

    private void titleParser(JsonNode node, TestCaseImporter imp) {
        node.fields().forEachRemaining(e -> {
            String key = e.getKey();
            switch(key) {
                case "prefix":
                    imp.testrunPrefix = e.getValue().textValue();
                    break;
                case "suffix":
                    imp.testrunSuffix = e.getValue().textValue();
                    break;
                default:
                    this.logger.error("Unknown key for title: %s", key);
            }
        });
    }

    private void pathsParser(JsonNode node) {
        node.fields().forEachRemaining(e -> {
            String key = e.getKey();
            if (key.equals("mapping"))
                this.mappingPath = e.getValue().textValue();
            else if (key.equals("xml")) {
                JsonNode n = e.getValue();
                n.fields().forEachRemaining(entry -> {
                    switch(entry.getKey()) {
                        case "testcases":
                            this.testcasesXMLPath = entry.getValue().textValue();
                            break;
                        case "requirements":
                            this.requirementsXMLPath = entry.getValue().textValue();
                            break;
                        default:
                            this.logger.error("Unknown value for xml: %s", entry.getKey());
                    }
                });
            }
        });
    }

    private void customParser(JsonNode node, XUnitImporter ximp) {
        node.fields().forEachRemaining(e -> {
            String key = e.getKey();
            JsonNode values = e.getValue();
            ArrayNode vals;
            switch (key) {
                case "test-suite":
                    if (values instanceof ArrayNode) {
                        vals = (ArrayNode) values;
                        vals.forEach(n -> {
                            n.fields().forEachRemaining(en -> {
                                ximp.testsuite.put(en.getKey(), en.getValue().toString());
                            });
                        });
                    }
                    break;
                case "properties":
                    if (values instanceof ArrayNode) {
                        vals = (ArrayNode) values;
                        vals.forEach(n -> {
                            n.fields().forEachRemaining(en -> {
                                ximp.properties.put(en.getKey(), en.getValue().toString());
                            });
                        });
                    }
                    break;
                default:
                    throw new Error("Must have key of 'test-suites' or 'properties'");
            }
        });
    }

    public static YAMLConfig load(Path configpath) throws IOException {
        if (configpath == null) {
            String homeDir = System.getProperty("user.home");
            configpath = FileSystems.getDefault().getPath(homeDir + "/.polarize/yaml-config.yaml");
        }
        YAMLConfig cfg = new YAMLConfig();
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        JsonNode root = mapper.readTree(configpath.toFile());
        cfg.parse(root);
        return cfg;
    }

    public ReporterConfig reportConfig() {
        ReporterConfig config = new ReporterConfig();
        Map<String, String> testsuite = this.xunitImporter.testsuite;
        config.setAuthor(this.author);
        config.setDryRun(Boolean.valueOf(testsuite.getOrDefault("dry-run", "false")));
        config.setIncludeSkipped(Boolean.valueOf(testsuite.getOrDefault("include-skipped", "false")));
        config.setResponseName(this.xunitImporter.selectorValue);
        config.setSetTestRunFinished(Boolean.valueOf(testsuite.getOrDefault("set-testrun-finished", "true")));
        config.setTestrunID(this.xunitImporter.testrunId);
        config.setTestrunTitle(this.xunitImporter.testrunTitle);
        config.setTestcasesXMLPath(this.testcasesXMLPath);
        config.setRequirementsXMLPath(this.requirementsXMLPath);
        config.setResponseTag(this.xunitImporter.selectorName);

        return config;
    }

    public static void main(String[] args) throws IOException {
        YAMLConfig cfg = YAMLConfig.load(null);
        System.out.println(cfg.author);
    }
}
