package com.github.redhatqe.polarize.reporter.configuration;


import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.redhatqe.byzantine.configuration.IConfig;
import com.github.redhatqe.byzantine.configuration.Serializer;
import com.github.redhatqe.byzantine.parser.Setter;
import com.github.redhatqe.byzantine.utils.Tuple;
import com.github.redhatqe.polarize.configuration.Config;
import com.github.redhatqe.polarize.exceptions.InvalidArgument;
import com.github.redhatqe.polarize.exceptions.XMLUnmarshallError;
import com.github.redhatqe.polarize.reporter.importer.ImporterRequest;
import com.github.redhatqe.polarize.reporter.importer.xunit.Property;
import com.github.redhatqe.polarize.reporter.importer.xunit.Testsuites;
import com.github.redhatqe.polarize.reporter.jaxb.IJAXBHelper;
import com.github.redhatqe.polarize.reporter.jaxb.JAXBHelper;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ReporterConfig implements IConfig {
    // =========================================================================
    // 1. Add all properties for your class/configuration
    // =========================================================================
    @JsonProperty(required=true)
    private String basedir;
    @JsonProperty(value="testcases-xml", required=true)
    private String testcasesXml;
    @JsonProperty(required=true)
    private String mapping;
    @JsonProperty(required=true)
    private String author;
    @JsonProperty(required=true)
    private String project;
    @JsonProperty(required=true)
    private Map<String, ServerInfo> servers;
    @JsonProperty(required=true)
    private XUnitInfo xunit;
    @JsonProperty(required=true)
    private Credentials kerberos;
    @JsonProperty(value="message-bus", required=true)
    private Config messageBus;
    //@JsonProperty
    //private String defaultBroker;

    // ==========================================================================
    // 2. Add all fields not belonging to the configuration here
    // ==========================================================================
    @JsonIgnore
    public Map<String, Setter<String>> sHandlers = new HashMap<>();
    @JsonIgnore
    public Map<String, Setter<Boolean>> bHandlers = new HashMap<>();
    @JsonIgnore
    public Map<String, Setter<Integer>> iHandlers = new HashMap<>();
    @JsonIgnore
    public final String properties = "properties";
    @JsonIgnore
    public final String testSuite = "test-suite";
    @JsonIgnore
    public static Logger logger = LogManager.getLogger("ReporterConfig");
    @JsonIgnore
    private String currentXUnit;
    @JsonIgnore
    private String newXunit;
    @JsonIgnore
    public final String polarionServer = "polarion";
    @JsonIgnore
    public final String brokerServer = "broker";
    @JsonIgnore
    private String newConfigPath = "";
    @JsonIgnore
    public Boolean showHelp = false;

    // =========================================================================
    // 3. Constructors go here.  Remember that there must be a no-arg constructor
    // =========================================================================
    public ReporterConfig() {
        this.servers = new HashMap<>();
        //this.setupDefaultHandlers();
    }

    /**
     * This should be like a copy constructor
     * @param cfg
     */
    public ReporterConfig(ReporterConfig cfg) {
        this();
        this.basedir = cfg.basedir;
        Map<String, ServerInfo> servers_ = cfg.getServers();
        servers_.forEach((String name, ServerInfo si) -> this.servers.put(name, new ServerInfo(si)));
        this.testcasesXml = cfg.getTestcasesXml();
        this.mapping = cfg.getMapping();
        this.author = cfg.getAuthor();
        this.project = cfg.getProject();
        this.xunit = cfg.getXunit();
        this.kerberos = cfg.getKerberos();
    }

    public Object deepCopy() {
        ReporterConfig cfg = new ReporterConfig();
        cfg.basedir = this.basedir;
        this.getServers().forEach((name, si) -> cfg.servers.put(name, new ServerInfo(si)));
        cfg.testcasesXml = this.getTestcasesXml();
        cfg.mapping = this.getMapping();
        cfg.author = this.getAuthor();
        cfg.project = this.getProject();
        cfg.xunit = this.getXunit();
        cfg.kerberos = this.getKerberos();
        return cfg;
    }

    //=============================================================================
    // 4. Define the bean setters and getters for all fields in #1
    //=============================================================================
    public String getTestcasesXml() {
        return testcasesXml;
    }

    public void setTestcasesXml(String testcasesXml) {
        this.testcasesXml = testcasesXml;
    }

    public String getMapping() {
        return mapping;
    }

    public void setMapping(String mapping) {
        this.mapping = mapping;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getProject() {
        return project;
    }

    public void setProject(String project) {
        this.project = project;
    }

    public Map<String, ServerInfo> getServers() {
        return servers;
    }

    public void setServers(Map<String, ServerInfo> servers) {
        this.servers = servers;
    }

    public XUnitInfo getXunit() {
        return xunit;
    }

    public void setXunit(XUnitInfo xunit) {
        this.xunit = xunit;
    }

    public Credentials getKerberos() {
        return kerberos;
    }

    public void setKerberos(Credentials kerberos) {
        this.kerberos = kerberos;
    }

    public String getBasedir() {
        return basedir;
    }

    public void setBasedir(String basedir) {
        this.basedir = basedir;
    }

    public String getCurrentXUnit() {
        return currentXUnit;
    }

    public void setCurrentXUnit(String currentXUnit) {
        this.currentXUnit = currentXUnit;
    }

    public String getNewXunit() {
        return newXunit;
    }

    public void setNewXunit(String newXunit) {
        this.newXunit = newXunit;
    }

    public String getNewConfigPath() {
        return newConfigPath;
    }

    public void setNewConfigPath(String newConfigPath) {
        this.newConfigPath = newConfigPath;
    }

    public Config getMessageBus() {
        return messageBus;
    }

    public void setMessageBus(Config messageBus) {
        this.messageBus = messageBus;
    }

    /**
    public String getDefaultBroker() {
        return defaultBroker;
    }

    public void setDefaultBroker(String defaultBroker) {
        this.defaultBroker = defaultBroker;
    }
     */

    //=============================================================================
    // 5. Define any functions for parsing the value of a command line opt and setting the values
    //=============================================================================
    /**
     * For any of the --property options, figure out what property we are setting by returning the string to the left
     * of the = sign
     *
     * @param value
     * @return
     */
    private Tuple<String, String> propertyParser(String value) {
        String[] vals = value.split("=", 2);
        Tuple<String, String> result = new Tuple<>();
        if (vals.length != 2) {
            result.first = value;
            result.second = value;
        }
        else {
            result.first = vals[0];
            result.second = vals[1];
        }
        return result;
    }

    private void propertyHandler(String val) {
        XUnitInfo xi = this.getXunit();
        Tuple<String, String> prop = this.propertyParser(val);
        xi.getCustom().getProperties().put(prop.first, prop.second);
    }


    /**
     * When using the --server option you can specify this multiple times.  It takes the form of:
     * --server serverName,user,password,(url)  the url is optional
     * So for example, to change the kerberos and polarion servers you would do:
     *
     * --server kerberos,platformqe_machine,secretpw
     * --server polarion,stoner,mypassword,http://10.0.0.1:5000/polarion/
     *
     * @param server
     * @return
     */
    private Tuple<String, ServerInfo> parseServer(String server) {
        ServerInfo st = new ServerInfo();
        Tuple<String, ServerInfo> result = new Tuple<>();

        String[] tokens = server.split(",");
        if (tokens.length < 3) {
            String err = "Must use --server with at least 3 args like:  --server kerberos,username,pw";
            logger.error(err);
            throw new InvalidArgument(err);
        }
        if (tokens[0].equals(""))
            throw new Error("First entry in comma separated list must be the name of the server");
        result.first = tokens[0];

        try {
            st.setUser(tokens[1]);
        }
        catch (ArrayIndexOutOfBoundsException oob) {
            logger.error("Must pass username as 2nd arg to --server");
            throw oob;
        }
        try {
            st.setPassword(tokens[2]);
        }
        catch (ArrayIndexOutOfBoundsException oob) {
            logger.error("Must specify the user's password as 3rd argument to --server");
            throw oob;
        }
        try {
            st.setUrl(tokens[3]);
        }
        catch (ArrayIndexOutOfBoundsException oob) {
            logger.info(String.format("Not setting a URL for %s", tokens[0]));
        }
        result.second = st;
        return result;
    }


    //=============================================================================
    // 6. implement the methods from IConfig
    //=============================================================================
    public void setupDefaultHandlers() {
        XUnitInfo.Testrun tr = this.getXunit().getTestrun();
        XUnitInfo xi = this.getXunit();

        // String option handlers
        this.addHandler(ReporterConfigOpts.PROJECT.getOption(), this::setProject, this.sHandlers);
        this.addHandler(ReporterConfigOpts.TESTRUN_ID.getOption(), tr::setId, this.sHandlers);
        this.addHandler(ReporterConfigOpts.TESTRUN_TITLE.getOption(), tr::setTitle, this.sHandlers);
        this.addHandler(ReporterConfigOpts.TESTRUN_TEMPLATE_ID.getOption(), tr::setTemplateId, this.sHandlers);
        this.addHandler(ReporterConfigOpts.TESTRUN_GROUP_ID.getOption(), tr::setGroupId, this.sHandlers);
        this.addHandler(ReporterConfigOpts.TESTRUN_TYPE.getOption(), tr::setType, this.sHandlers);
        this.addHandler(ReporterConfigOpts.XUNIT_SELECTOR_NAME.getOption(),
                (s) -> xi.getSelector().setName(s),
                this.sHandlers);
        this.addHandler(ReporterConfigOpts.XUNIT_SELECTOR_VAL.getOption(),
                (s) -> xi.getSelector().setValue(s),
                this.sHandlers);
        this.addHandler(ReporterConfigOpts.BASE_DIR.getOption(), this::setBasedir, this.sHandlers);
        this.addHandler(ReporterConfigOpts.MAPPING.getOption(), this::setMapping, this.sHandlers);
        this.addHandler(ReporterConfigOpts.CURRENT_XUNIT.getOption(), this::setCurrentXUnit, this.sHandlers);
        this.addHandler(ReporterConfigOpts.NEW_XUNIT.getOption(), this::setNewXunit, this.sHandlers);
        this.addHandler(ReporterConfigOpts.JENKINSJOBS.getOption(),
                (s) -> xi.getCustom().getProperties().put(ReporterConfigOpts.JENKINSJOBS.getOption(), s),
                this.sHandlers);
        this.addHandler(ReporterConfigOpts.NOTES.getOption(),
                (s) -> xi.getCustom().getProperties().put(ReporterConfigOpts.NOTES.getOption(), s),
                this.sHandlers);
        this.addHandler(ReporterConfigOpts.SERVER.getOption(), (s) -> {
            Tuple<String, ServerInfo> res = this.parseServer(s);
            Map<String, ServerInfo> servers = this.getServers();
            servers.put(res.first, res.second);
        }, this.sHandlers);
        this.addHandler(ReporterConfigOpts.TR_PROPERTY.getOption(), this::propertyHandler, this.sHandlers);
        this.addHandler(ReporterConfigOpts.EDIT_CONFIG.getOption(), this::setNewConfigPath, this.sHandlers);
        this.addHandler(ReporterConfigOpts.USERNAME.getOption(), (s) -> this.getKerberos().setUser(s), this.sHandlers);
        this.addHandler(ReporterConfigOpts.USERPASSWORD.getOption(),
                (s) -> this.getKerberos().setPassword(s),
                this.sHandlers);

        // Boolean option handlers
        this.addHandler(ReporterConfigOpts.TC_IMPORTER_ENABLED.getOption(),
                (b) -> xi.getCustom().getTestSuite().put(ReporterConfigOpts.TC_IMPORTER_ENABLED.getOption(), b),
                this.bHandlers);
        this.addHandler(ReporterConfigOpts.XUNIT_IMPORTER_ENABLED.getOption(),
                (b) -> xi.getCustom().getTestSuite().put(ReporterConfigOpts.XUNIT_IMPORTER_ENABLED.getOption(), b),
                this.bHandlers);
        this.addHandler(ReporterConfigOpts.TR_SET_FINISHED.getOption(),
                (b) -> xi.getCustom().getTestSuite().put(ReporterConfigOpts.TR_SET_FINISHED.getOption(), b),
                this.bHandlers);
        this.addHandler(ReporterConfigOpts.TR_INCLUDE_SKIPPED.getOption(),
                (b) -> xi.getCustom().getTestSuite().put(ReporterConfigOpts.TR_SET_FINISHED.getOption(), b),
                this.bHandlers);
        this.addHandler(ReporterConfigOpts.TR_DRY_RUN.getOption(),
                (b) -> xi.getCustom().getTestSuite().put(ReporterConfigOpts.TR_DRY_RUN.getOption(), b),
                this.bHandlers);

        // Integer handlers
        this.addHandler(ReporterConfigOpts.XUNIT_IMPORTER_TIMEOUT.getOption(), xi::setTimeout, this.iHandlers);

    }

    /**
     * Generates
     * @param path
     */
    @Override
    public void writeConfig(String path) {
        if (path.endsWith(".yaml") || path.endsWith(".yml"))
            try {
                Serializer.toYaml(this, path);
            } catch (IOException e) {
                e.printStackTrace();
            }
        else if (path.endsWith(".json"))
            try {
                Serializer.toJson(this, path);
            } catch (IOException e) {
                e.printStackTrace();
            }
    }

    @Override
    public Map<String, Setter<String>> sGetHandlers() {
        return this.sHandlers;
    }

    @Override
    public Map<String, Setter<Boolean>> bGetHandlers() {
        return this.bHandlers;
    }

    @Override
    public Map<String, Setter<Integer>> iGetHandlers() {
        return this.iHandlers;
    }

    public void setHelp(Boolean help) {
        this.showHelp = help;
    }

    // ==================================================================================
    // 7. Implement misc methods
    // ==================================================================================
    public static void test(String[] args) throws IOException {
        ReporterConfig cfg = new ReporterConfig();
        cfg.setAuthor("Sean toner");
        cfg.setMapping("/home/stoner/Projects/mapping.json");

        XUnitInfo xi = new XUnitInfo();
        xi.setEndpoint("/importers/xunit");
        xi.setTimeout(10000);

        Selector sel = new Selector();
        sel.setName("rhsm_qe");
        sel.setValue("xunit_importer");
        xi.setSelector(sel);
        xi.setEnabled(true);

        XUnitInfo.Testrun tr = xi.new Testrun();
        tr.setId("");
        tr.setTitle("");
        xi.setTestrun(tr);

        XUnitInfo.Custom cu = xi.getCustom();
        Map<String, Boolean> ts = new HashMap<>();
        Map<String, String> props = new HashMap<>();
        ts.put("include-skipped", true);
        props.put("planned-in", "RHEL_74_snap1");
        cu.setTestSuite(ts);
        cu.setProperties(props);
        xi.setCustom(cu);

        ServerInfo si = new ServerInfo("http:/belch", "stoner", "foo");
        ServerInfo broker = new ServerInfo("http://psychedilic/man", "whoops", "shazam");
        Map<String, ServerInfo> servers = cfg.getServers();
        servers.put("polarion-devel", si);
        servers.put("broker", broker);

        cfg.setXunit(xi);
        cfg.setServers(servers);

        Serializer.toYaml(cfg, "/tmp/reporter.yaml");

        System.out.println("Done serializing to yaml");

        File cfgFile = new File("/tmp/polarize-reporter-configuration.yml");
        ReporterConfig rcfg = Serializer.fromYaml(ReporterConfig.class, cfgFile);
        System.out.println("Done marshalling to POJO");
    }

    public List<Property> propsFromTestRun(XUnitInfo.Testrun tr) {
        List<Tuple<String, Supplier<String>>> getters = new ArrayList<>();
        getters.add(new Tuple<>("title", tr::getTitle));
        getters.add(new Tuple<>("id", tr::getId));
        getters.add(new Tuple<>("template-id", tr::getTemplateId));
        getters.add(new Tuple<>("type-id", tr::getType));
        getters.add(new Tuple<>("group-id", tr::getGroupId));

        return  getters.stream()
                .map(pair -> {
                    Property prop = new Property();
                    prop.setName("polarion-testrun-" + pair.first);
                    prop.setValue(pair.second.get());
                    return prop;
                })
                .collect(Collectors.toList());
    }

    public List<Property> makePropList() {
        List<Property> props = new ArrayList<>();
        Property project = new Property();
        project.setName("polarion-project-id");
        project.setValue(this.getProject());
        props.add(project);

        Property selector = new Property();
        selector.setName("polarion-response-" + this.getXunit().getSelector().getName());
        selector.setValue(this.getXunit().getSelector().getValue());
        props.add(selector);

        // all the polarion-testrun-* properties
        props.addAll(this.propsFromTestRun(this.getXunit().getTestrun()));

        // dry-run, set-finished and include-skipped values
        props.addAll(this.getXunit().getCustom().getTestSuite().entrySet().stream()
                .map(es -> {
                    Property prop = new Property();
                    prop.setName("polarion-" + es.getKey());
                    prop.setValue(es.getValue().toString());
                    return prop;
                })
                .collect(Collectors.toList()));

        // all the polarion-custom-* properties
        props.addAll(this.getXunit().getCustom().getProperties().entrySet().stream()
                .map(es -> {
                    Property prop = new Property();
                    prop.setName("polarion-custom-" + es.getKey());
                    prop.setValue(es.getValue());
                    return prop;
                })
                .collect(Collectors.toList()));
        return props;
    }

    /**
     * Given an existing XML file for the Testsuite, edit it according to the params
     *
     * @param tsPath existing path for Testsuite
     * @param newpath path for newly modified testsuite
     */
    public void editTestSuite(String tsPath, String newpath) throws IOException {
        File xunit = new File(tsPath);
        if (tsPath.startsWith("https")) {

            String user = this.servers.get(polarionServer).getUser();
            String pw = this.servers.get(polarionServer).getPassword();
            Optional<File> maybeXunit = ImporterRequest.get(tsPath, user, pw, newpath);
            if (maybeXunit.isPresent())
                xunit = maybeXunit.get();
            else
                throw new IOException("Could not download " + tsPath);
        }
        else if (tsPath.startsWith("http"))
            xunit = ImporterRequest.download(tsPath, "/tmp/tmp-polarion.xml");
        if (!xunit.exists())
            throw new InvalidArgument(tsPath + " does not exist");
        JAXBHelper jaxb = new JAXBHelper();
        Optional<Testsuites> ts = IJAXBHelper.unmarshaller(Testsuites.class, xunit,
                jaxb.getXSDFromResource(Testsuites.class));
        if (!ts.isPresent())
            throw new XMLUnmarshallError();

        Testsuites suites = ts.get();
        List<Property> props = suites.getProperties().getProperty();
        props.removeIf(p -> p.getName().equals("polarion-user-id"));
        List<Property> fromConfig = this.makePropList();

        List<Property> newprops = new ArrayList<>();
        for(Property p: fromConfig) {
            boolean matched = false;
            for(Property p2: props) {
                matched = p.getName().equals(p2.getName());
                if (matched) {
                    String val = p.getValue();
                    p2.setValue(val);
                    break;
                }
            }
            if (!matched)
                newprops.add(p);
        }
        props.addAll(newprops);
        props.removeIf(p -> p.getValue() == null || p.getValue().equals(""));

        File newxunit = new File(newpath);
        IJAXBHelper.marshaller(suites ,newxunit, jaxb.getXSDFromResource(Testsuites.class));

        ObjectMapper mapper = new ObjectMapper();
        try {
            mapper.writer().withDefaultPrettyPrinter().writeValue(new File("/tmp/testsuites.json"), suites);
            //mapper.writer().withDefaultPrettyPrinter().writeValue(new File("/tmp/configuration.yaml"), this);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
