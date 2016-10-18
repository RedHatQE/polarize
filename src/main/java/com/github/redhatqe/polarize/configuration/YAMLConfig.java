package com.github.redhatqe.polarize.configuration;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.github.redhatqe.polarize.metadata.DefTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Created by stoner on 10/13/16.
 */
public class YAMLConfig {
    @JsonProperty(value="polarion")
    public Server polarionServer;
    @JsonProperty(value="kerberos")
    public Server kerbServer;
    @JsonProperty(value="ossrh")
    public Server ossrhServer;
    @JsonProperty(value="testcases-xml")
    public String testcasesXMLPath;
    @JsonProperty(value="requirements-xml")
    public String requirementsXMLPath;
    @JsonProperty(value="mapping")
    public String mappingPath;
    @JsonProperty(value="testcase")
    public TestCaseImporter tcImporter;
    @JsonProperty(value="xunit")
    public XUnitImporter xunitImporter;

    public String author;
    public String project;
    @JsonIgnore
    public Logger logger;


    public YAMLConfig() {
        this.logger = LoggerFactory.getLogger(YAMLConfig.class);
    }

    public class Server {
        public String url = "";
        public String user;
        public String password;
    }

    public class CustomProps {
        public Boolean dryRun;
        public Boolean setTestRunFinished;
        public Boolean includeSkipped;
    }

    public class ImportType {
        @JsonProperty(value="testcase")
        public TestCaseImporter testcase;
        @JsonProperty(value="xunit")
        public XUnitImporter xunit;

        public ImportType() {
            YAMLConfig.this.logger.info("In ImportType constructor");
        }
    }

    @JsonProperty(value="importer")
    public void setImportType(ImportType it) {
        this.tcImporter = it.testcase;
        this.xunitImporter = it.xunit;
    }

    public class Endpoint {
        public String endpoint;

        public Endpoint(String ep) {
            endpoint = ep;
        }

        @JsonProperty(value="endpoint")
        public void setEndpoint(Endpoint ep) {
            endpoint = ep.endpoint;
        }
    }

    public class FileType {
        @JsonProperty(value="file")
        public String file;

        public FileType(String f) {
            file = f;
        }
    }

    public abstract class Importer {
        public String author;
        public DefTypes.Project project;
        @JsonProperty(value="endpoint")
        public Endpoint ep;
        public String endpoint;
        public Selector select;
        public String selector;
        public String selectorName;
        public String selectorValue;
        public FileType fileType;
        public String file;
        public Integer timeout;

        public Importer(String ep) {
            System.out.println("In Importer");
            this.ep = new Endpoint(ep);
        }

        public class Selector {
            @JsonProperty(value="name")
            public String name;
            @JsonProperty(value="value")
            public String value;
        }

        @JsonProperty(value="selector")
        public void setSelector(Selector s) {
            select = s;
            selectorName = s.name;
            selectorValue = s.value;
            selector = String.format("%s='%s'", s.name, s.value);
        }

        @JsonProperty(value="file")
        public void setFile(FileType ft) {
            fileType = ft;
            file = ft.file;
        }
    }

    @JsonRootName(value="xunit")
    public class XUnitImporter extends Importer {
        public Map<String, String> testsuite = new HashMap<>();
        // TODO:  Try to find all valid custom fields and make this an enumeration
        public Map<String, String> properties = new HashMap<>();
        public TestRun testrun;
        public String testrunId;
        public String testrunTitle;
        public Custom custom;
        public CustomProps props;

        public XUnitImporter(String ept) {
            super(ept);
            YAMLConfig.this.logger.info("In XUnitImporter constructor");
        }

        public class TestRun {
            public String id;
            public String title;
        }

        @JsonProperty(value="testrun")
        public void setTestRun(TestRun tr) {
            testrun = tr;
            testrunId = tr.id;
            testrunTitle = tr.title;
        }

        public class Custom {
            @JsonProperty(value="test-suite")
            public TestSuite testsuite;
            @JsonProperty(value="properties")
            public  Properties properties;

            public class TestSuite {
                List<Map<String, String>> entries;
                Map<String, String> fields;

                @JsonProperty(value="testsuite")
                public void setEntries(TestSuite ts) {
                    YAMLConfig.this.logger.info(ts.toString());
                }
            }

            public class Properties {

            }
        }

        @JsonProperty(value="custom")
        public void setCustom(Custom c) {
            custom = c;
        }
    }

    @JsonRootName(value="testcase")
    public class TestCaseImporter extends Importer {
        public Title title;
        public String testrunPrefix;
        public String testrunSuffix;

        public TestCaseImporter(String ep) {
            super(ep);
            YAMLConfig.this.logger.info("In TestCaseImporter");
            this.ep = new Endpoint(ep);
        }


        public class Title {
            public String prefix;
            public String suffix;
        }

        @JsonProperty(value="title")
        public void setTitle(Title t) {
            title = t;
            testrunSuffix = t.suffix;
            testrunPrefix = t.prefix;
        }
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
                case "testcases":
                    this.testcasesXMLPath = entry.getValue().textValue();
                    break;
                case "requirements":
                    this.requirementsXMLPath = entry.getValue().textValue();
                    break;
                case "mapping":
                    this.mappingPath = entry.getValue().textValue();
                case "importer":
                    this.importerHandler(value, null);
                    break;
                default:
                    this.logger.error("Unknown root key: %s", key);
                    //System.err.println(String.format("Unknown root key: %s", key));
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
                    if (importer instanceof TestCaseImporter) this.titleParser(value, (TestCaseImporter) importer);
                    break;
                case "timeout":
                    importer.timeout = Integer.parseInt(value.toString());
                    break;
                case "testrun":
                    if (importer instanceof XUnitImporter) this.testrunParser(value, (XUnitImporter) importer);
                    break;
                case "custom":
                    if (importer instanceof XUnitImporter) this.customParser(value, (XUnitImporter) importer);
                    break;
                default:
                    this.logger.error(String.format("Unknown key for root: %s", key));
                    //System.err.println(String.format("Unknown key for root: %s", key));
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
                //System.err.println(String.format("Unknown key for selector: %s", n.getKey()));
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
                    //System.err.println(String.format("Unknown key for testrun: %s", key));
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
                    //System.err.println(String.format("Unknown key for title: %s", key));
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
                            //System.err.println(String.format("Unknown value for xml: %s", entry.getKey()));
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
        ximp.testsuite.entrySet().forEach(es -> {
            String k = es.getKey();
            switch(k) {
                case "dry-run":
                    ximp.props.dryRun = Boolean.valueOf(es.getValue());
                    break;
                case "set-testrun-finished":
                    ximp.props.setTestRunFinished = Boolean.valueOf(es.getValue());
                    break;
                case "include-skipped":
                    ximp.props.includeSkipped = Boolean.valueOf(es.getValue());
                    break;
                default:
                    this.logger.error("Unknown field for test-suites");
                    //System.err.println("Unknown field for test-suites");
            }
        });
    }

    /**
     * Creates a YAMLConfig object from a given yaml file
     *
     * @param configpath path to the yaml file
     * @return
     */
    public static YAMLConfig load(Path configpath) {
        if (configpath == null) {
            String homeDir = System.getProperty("user.home");
            configpath = FileSystems.getDefault().getPath(homeDir + "/.polarize/yaml-config.yml");
        }
        YAMLConfig cfg = new YAMLConfig();
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        JsonNode root = null;
        try {
            root = mapper.readTree(configpath.toFile());
            cfg.parse(root);
        } catch (IOException e) {
            throw new Error("IOException thrown reading from yaml config file");
        }
        return cfg;
    }

    /**
     *
     */
    public void store(File yaml) {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        ObjectWriter writer = mapper.writerWithDefaultPrettyPrinter();
        try {
            String text = writer.writeValueAsString(this);
            //this.logger.info(text);
            System.out.println(text);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws IOException {
        //YAMLConfig cfg = YAMLConfig.load(null);
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        YAMLConfig cfg = mapper.readValue(new File("/home/stoner/.polarize/yaml-config.yml"), YAMLConfig.class);
        System.out.println(cfg.author);
        cfg.store(null);
    }
}
