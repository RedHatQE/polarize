package com.github.redhatqe.polarize.configuration;

import com.github.redhatqe.polarize.IJAXBHelper;
import com.github.redhatqe.polarize.JAXBHelper;
import com.github.redhatqe.polarize.exceptions.ConfigurationError;
import com.github.redhatqe.polarize.exceptions.InvalidArgument;
import com.github.redhatqe.polarize.exceptions.XMLUnmarshallError;
import com.github.redhatqe.polarize.exceptions.XSDValidationError;

import com.github.redhatqe.polarize.importer.xunit.Property;
import com.github.redhatqe.polarize.importer.xunit.Testsuite;
import com.github.redhatqe.polarize.importer.xunit.Testsuites;
import com.github.redhatqe.polarize.utils.Tuple;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Class that defines an API to edit the xml-config.xml file
 */
public class Configurator implements IJAXBHelper {
    public Logger logger;
    public OptionParser parser;
    public XMLConfig config;
    public ConfigType cfg;
    public String configPath;

    private String testrunTitle;
    private String project;
    private String testcasePrefix;
    private String testcaseSuffix;
    private String plannedin;
    private String jenkinsjobs;
    private String notes;
    private String templateId;
    private String tcSelectorName;
    private String tcSelectorVal;
    private String xunitSelectorName;
    private String xunitSelectorVal;

    private Boolean testcaseImporterEnabled;
    private Boolean xunitImporterEnabled;
    private Boolean testrunDryRun;
    private Boolean testrunSetFinished;
    private Boolean testrunIncludeSkipped;

    private Integer testcaseTimeout;
    private Integer xunitTimeout;

    public String tcPath;
    public List<ServerType> servers;

    public Map<String, Tuple<Getter<String>, Setter<String>>> sOptToAccessors = new HashMap<>();
    public Map<String, Tuple<Getter<Boolean>, Setter<Boolean>>> bOptToAccessors = new HashMap<>();
    public Map<String, Tuple<Getter<Integer>,Setter<Integer>>> iOptToAccessors = new HashMap<>();
    public Map<String, OptionSpec<String>> sSpecs = new HashMap<>();
    public Map<String, OptionSpec<Boolean>> bSpecs = new HashMap<>();
    public Map<String, OptionSpec<Integer>> iSpecs = new HashMap<>();
    public Map<String, String> testsuiteProps = new HashMap<>();
    public List<Property> customProps;

    public OptionSet opts;

    public Configurator() {
        this.configPath = java.lang.System.getProperty("user.home") + "/.polarize/xml-config.xml";
        this.logger = LoggerFactory.getLogger(Configurator.class);
        this.config = new XMLConfig(null);
        this.cfg = this.config.config;
        this.parser = new OptionParser();
        this.servers = new ArrayList<>();
        this.configureParser();
    }

    public Configurator(String configPath) {
        this.configPath = configPath;
        this.logger = LoggerFactory.getLogger(Configurator.class);
        this.config = new XMLConfig(new File(configPath));
        this.cfg = this.config.config;
        this.parser = new OptionParser();
        this.servers = new ArrayList<>();
        this.configureParser();
    }

    public void configureParser() {
        sOptToAccessors.put(Opts.TESTRUN_TITLE, new Tuple<>(this::getTestrunTitle, this::setTestrunTitle));
        sOptToAccessors.put(Opts.PROJECT, new Tuple<>(this::getProject, this::setProject));
        sOptToAccessors.put(Opts.TESTCASE_PREFIX, new Tuple<>(this::getTestcasePrefix, this::setTestcasePrefix));
        sOptToAccessors.put(Opts.TESTCASE_SUFFIX, new Tuple<>(this::getTestcaseSuffix, this::setTestcaseSuffix));
        sOptToAccessors.put(Opts.PLANNEDIN, new Tuple<>(this::getPlannedin, this::setPlannedin));
        sOptToAccessors.put(Opts.JENKINSJOBS, new Tuple<>(this::getJenkinsjobs, this::setJenkinsjobs));
        sOptToAccessors.put(Opts.NOTES, new Tuple<>(this::getNotes, this::setNotes));
        sOptToAccessors.put(Opts.TEMPLATE_ID, new Tuple<>(this::getTemplateId, this::setTemplateId));
        sOptToAccessors.put(Opts.TC_SELECTOR_NAME, new Tuple<>(this::getTcSelectorName, this::setTcSelectorName));
        sOptToAccessors.put(Opts.TC_SELECTOR_VAL, new Tuple<>(this::getTcSelectorVal, this::setTcSelectorVal));
        sOptToAccessors.put(Opts.XUNIT_SELECTOR_NAME, new Tuple<>(this::getXunitSelectorName,
                                                                  this::setXunitSelectorName));
        sOptToAccessors.put(Opts.XUNIT_SELECTOR_VAL, new Tuple<>(this::getXunitSelectorVal, this::setXunitSelectorVal));

        bOptToAccessors.put(Opts.TC_IMPORTER_ENABLED, new Tuple<>(this::getTestcaseImporterEnabled,
                this::setTestcaseImporterEnabled));
        bOptToAccessors.put(Opts.XUNIT_IMPORTER_ENABLED, new Tuple<>(this::getXunitImporterEnabled,
                this::setXunitImporterEnabled));
        bOptToAccessors.put(Opts.TR_DRY_RUN, new Tuple<>(this::getTestrunDryRun, this::setTestrunDryRun));
        bOptToAccessors.put(Opts.TR_SET_FINISHED, new Tuple<>(this::getTestrunSetFinished,
                this::setTestrunSetFinished));
        bOptToAccessors.put(Opts.TR_INCLUDE_SKIPPED, new Tuple<>(this::getTestrunIncludeSkipped,
                this::setTestrunIncludeSkipped));

        iOptToAccessors.put(Opts.TC_IMPORTER_TIMEOUT, new Tuple<>(this::getTestcaseTimeout,
                this::setTestcaseTimeout));
        iOptToAccessors.put(Opts.XUNIT_IMPORTER_TIMEOUT, new Tuple<>(this::getXunitTimeout, this::setXunitTimeout));

        bOptToAccessors.keySet().forEach(b -> {
            bSpecs.put(b, this.parser.accepts(b).withRequiredArg().ofType(Boolean.class));
        });
        sOptToAccessors.keySet().forEach(s -> {
            sSpecs.put(s, this.parser.accepts(s).withRequiredArg().ofType(String.class));
        });
        iOptToAccessors.keySet().forEach(i -> {
            iSpecs.put(i, this.parser.accepts(i).withRequiredArg().ofType(Integer.class));
        });

        // Non standard parsing required for these
        sSpecs.put(Opts.SERVER, this.parser.accepts(Opts.SERVER).withRequiredArg().ofType(String.class));
        sSpecs.put(Opts.TR_PROPERTY, this.parser.accepts(Opts.TR_PROPERTY).withRequiredArg().ofType(String.class));
    }

    /**
     *
     * @param args
     */
    public void parse(String[] args){
        OptionSet opts = parser.parse(args);
        this.opts = opts;
        this.sOptToAccessors.entrySet().forEach(es -> {
            String opt = es.getKey();
            OptionSpec<String> spec = this.sSpecs.get(opt);
            if (this.opts.has(spec)) {
                String val = (String) opts.valueOf(opt);
                Setter<String> s = es.getValue().second;
                s.set(val);
            }
        });

        this.bOptToAccessors.entrySet().forEach(es -> {
            String opt = es.getKey();
            if (this.bSpecs.containsKey(opt) && this.opts.has(this.bSpecs.get(opt))) {
                Boolean val = opts.valueOf(this.bSpecs.get(opt));
                Setter<Boolean> s = es.getValue().second;
                s.set(val);
            }
        });

        this.iOptToAccessors.entrySet().forEach(es -> {
            String opt = es.getKey();
            if (this.iSpecs.containsKey(opt) && this.opts.has(this.iSpecs.get(opt))) {
                Integer val = opts.valueOf(this.iSpecs.get(opt));
                Setter<Integer> s = es.getValue().second;
                s.set(val);
            }
        });

        if (this.opts.has(Opts.PROJECT)) {
            String project = opts.valueOf(this.sSpecs.get(Opts.PROJECT));
            this.cfg.setProject(project);
        }
        this.setConfigType();
        this.setServers(opts);

        List<String> properties = opts.valuesOf(this.sSpecs.get(Opts.TR_PROPERTY));
        customProps = properties.stream()
                .map(this::parseProperty)
                .collect(Collectors.toList());
    }

    private void setServers(OptionSet opts) {
        OptionSpec<String> serverSpec = this.sSpecs.get(Opts.SERVER);
        List<String> vals = serverSpec.values(opts);
        List<ServerType> current = this.cfg.getServers().getServer();
        Optional<ServerType> matched;
        for(String v: vals) {
            ServerType st = this.parseServer(v);
            matched = current.stream()
                    .filter(s -> s.getName().equals(st.getName()))
                    .findFirst();
            if (matched.isPresent()) {
                ServerType m = matched.get();
                m.setUrl(m.getUrl());
                m.setUser(m.getUser());
                m.setPassword(m.getPassword());
            }
        }
    }

    public void setConfigType() {
        this.cfg.setProject(this.getProject());
        ImportersType importers = this.cfg.getImporters();
        List<ImporterType> importerList = importers.getImporter();
        for(ImporterType imp: importerList) {
            String iType = imp.getType();
            if (iType.equals("testcase"))
                this.setTCImporter(imp);
            else
                this.setXunitImporter(imp);
        }
    }

    private void setCustomProperties(List<PropertyType> props) {
        for(PropertyType p: props) {
            String name = p.getName();
            String keyname = String.format("polarion-custom-%s", name);
            switch (name) {
                case Opts.PLANNEDIN:
                    String pi = this.getPlannedin();
                    if (pi != null)
                        p.setVal(pi);
                    this.testsuiteProps.put(keyname, pi);
                    break;
                case Opts.JENKINSJOBS:
                    String jj = this.getJenkinsjobs();
                    if (jj != null)
                        p.setVal(jj);
                    this.testsuiteProps.put(keyname, jj);
                    break;
                case Opts.NOTES:
                    String n = this.getNotes();
                    if (n != null)
                        p.setVal(n);
                    this.testsuiteProps.put(keyname, n);
                    break;
                default:
                    this.logger.error("Unknown property name %s", name);
                    break;
            }
        }
    }

    private void setTestRunProperties(List<PropertyType> tsProps) {
        for(PropertyType p: tsProps) {
            String name = p.getName();
            switch(name) {
                case "dry-run":
                    Boolean dryRun = this.getTestrunDryRun();
                    if (dryRun != null)
                        p.setVal(dryRun.toString());
                    break;
                case "include-skipped":
                    Boolean skipped = this.getTestrunIncludeSkipped();
                    if (skipped != null)
                        p.setVal(skipped.toString());
                    break;
                case "set-testrun-finished":
                    Boolean trFinish = this.getTestrunSetFinished();
                    if (trFinish != null)
                        p.setVal(trFinish.toString());
                    break;
                default:
                    this.logger.error("Unknown property name %s", name);
            }
        }
    }


    public void setTCImporter(ImporterType imp) {
        if (this.opts.has(iSpecs.get(Opts.TC_IMPORTER_TIMEOUT))) {
            Integer timeout = this.opts.valueOf(iSpecs.get(Opts.TC_IMPORTER_TIMEOUT));
            imp.getTimeout().setMillis(timeout.toString());
            this.setTestcaseTimeout(timeout);
        }
        if (this.opts.has(bSpecs.get(Opts.TC_IMPORTER_ENABLED))) {
            Boolean enabled = this.opts.valueOf(bSpecs.get(Opts.TC_IMPORTER_ENABLED));
            this.setTestcaseImporterEnabled(enabled);
            imp.setEnabled(enabled);
        }

        this.setSelectorName(imp, Opts.TC_SELECTOR_NAME, Opts.TC_SELECTOR_VAL);

        if (this.opts.has(sSpecs.get(Opts.TESTCASE_PREFIX))) {
            TitleType title = imp.getTitle();
            title.setPrefix(this.getTestcasePrefix());
            imp.setTitle(title);
        }

        if (this.opts.has(sSpecs.get(Opts.TESTCASE_SUFFIX))) {
            TitleType title = imp.getTitle();
            title.setSuffix(this.getTestcaseSuffix());
            imp.setTitle(title);
        }
    }

    public void setXunitImporter(ImporterType imp) {
        List<PropertyType> customProps = imp.getCustomFields().getProperty();
        this.setCustomProperties(customProps);

        List<PropertyType> tsProps = imp.getTestSuite().getProperty();
        this.setTestRunProperties(tsProps);

        if (this.opts.has(iSpecs.get(Opts.XUNIT_IMPORTER_TIMEOUT)))
            imp.getTimeout().setMillis(this.getXunitTimeout().toString());
        if (this.opts.has(bSpecs.get(Opts.XUNIT_IMPORTER_ENABLED)))
            imp.setEnabled(this.getXunitImporterEnabled());

        if (this.opts.has(sSpecs.get(Opts.TESTRUN_TITLE))) {
            TestrunType tr = imp.getTestrun();
            tr.setTitle(this.getTestrunTitle());
            imp.setTestrun(tr);
        }

        if (this.opts.has(sSpecs.get(Opts.TEMPLATE_ID))) {
            TemplateIdType tid = imp.getTemplateId();
            tid.setId(this.getTemplateId());
            imp.setTemplateId(tid);
        }

        this.setSelectorName(imp, Opts.XUNIT_SELECTOR_NAME, Opts.XUNIT_SELECTOR_VAL);
    }

    public void setSelectorName(ImporterType imp, String name, String val) {
        if (sSpecs.containsKey(name) && this.opts.has(sSpecs.get(name))) {
            SelectorType st = imp.getSelector();
            if (imp.getType().equals("xunit"))
                st.setName(this.getXunitSelectorName());
            else
                st.setName(this.getTcSelectorName());
            imp.setSelector(st);
        }

        if (sSpecs.containsKey(val) && this.opts.has(sSpecs.get(val))) {
            SelectorType st = imp.getSelector();
            String sval = this.opts.valueOf(this.sSpecs.get(val));
            st.setVal(sval);
            imp.setSelector(st);
        }
    }


    public ServerType parseServer(String server) {
        ServerType st = new ServerType();

        String[] tokens = server.split(",");
        if (tokens[0].equals(""))
            throw new ConfigurationError("First entry in comma separated list must be the name of the server");
        st.setName(tokens[0]);
        st.setUser(tokens[1]);
        st.setPassword(tokens[2]);
        st.setUrl(tokens[3]);
        return st;
    }

    /**
     * This is a string in the command line like --property plannedin=RHEL_7_3
     * @param cust
     */
    public Property parseProperty(String cust) {
        String[] tokens = cust.split("=");
        if (tokens.length != 2)
            throw new InvalidArgument("--property must be in key=value form");
        Property prop = new Property();
        prop.setName(String.format("polarion-custom-%s", tokens[0]));
        prop.setValue(tokens[1]);

        Optional<ImporterType> maybeImp =  this.cfg.getImporters().getImporter()
                .stream().filter(i -> i.getType().equals("xunit")).findFirst();
        if (!maybeImp.isPresent())
            throw new ConfigurationError("Could not find <importer type='xunit'>");

        ImporterType imp = maybeImp.get();
        List<PropertyType> properties = imp.getCustomFields().getProperty();
        boolean matched = false;
        for(PropertyType p: properties) {
            matched = p.getName().equals(tokens[0]);
            if (matched) {
                p.setVal(tokens[1]);
                break;
            }
        }
        if (!matched) {
            PropertyType pt = new PropertyType();
            pt.setName(tokens[0]);
            pt.setVal(tokens[1]);
            properties.add(pt);
        }
        return prop;
    }

    /**
     * Writes out the config to the given path
     * @param path
     */
    public static void writeOut(String path, ConfigType cfg) {
        File config = new File(path);
        JAXBHelper jaxb = new JAXBHelper();
        IJAXBHelper.marshaller(cfg, config, jaxb.getXSDFromResource(ConfigType.class));
    }

    public class Opts {
        public static final String TESTRUN_TITLE = "testrun-title";
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
        public static final String TR_DRY_RUN = "testrun-dry-run";
        public static final String TR_SET_FINISHED = "testrun-set-finished";
        public static final String TR_INCLUDE_SKIPPED = "testrun-include-skipped";

        public static final String TC_IMPORTER_TIMEOUT = "testcase-importer-timeout";
        public static final String XUNIT_IMPORTER_TIMEOUT = "xunit-importer-timeout";
        public static final String TR_PROPERTY = "property";

        // This option takes the form of name,user,pw,url.  If any are missing, leave it empty. name is required
        // --server polarion,ci-user,&$Err,http://some/url
        // --server kerb,stoner,myP@ss,
        // --server ossrh,stoner,ossrh-p@ss,
        public static final String SERVER = "server";
    }

    public enum TestsuiteProps {
        USER("polarion-user-id"),
        PROJECT("polarion-project-id"),
        TESTRUN_FINISHED("polarion-set-testrun-finished"),
        DRY_RUN("polarion-dry-run"),
        INCLUDE_SKIPPED("polarion-include-skipped"),
        RESPONSE("polarion-response"),
        TESTRUN_TITLE("polarion-testrun-title"),
        TESTRUN_ID("polarion-testrun-id"),
        TEMPLATE_ID("polarion-template-id");

        public final String value;
        TestsuiteProps(String val) {
            value = val;
        }

        public String toString() {
            return this.value;
        }

        public static TestsuiteProps fromString(String s) {
            switch(s) {
                case "polarion-user-id":
                    return USER;
                case "polarion-project-id":
                    return PROJECT;
                case "polarion-set-testrun-finished":
                    return TESTRUN_FINISHED;
                case "polarion-dry-run":
                    return DRY_RUN;
                case "polarion-include-skipped":
                    return INCLUDE_SKIPPED;
                case "polarion-testrun-title":
                    return TESTRUN_TITLE;
                case "polarion-testrun-id":
                    return TESTRUN_ID;
                case "polarion-template-id":
                    return TEMPLATE_ID;
                default:
                    if (s.contains("polarion-response"))
                        return RESPONSE;
                    else
                        throw new java.lang.IllegalArgumentException(String.format("No enum constant for %s", s));
            }
        }
    }

    /**
     * Backs up the original xml-config.xml
     */
    public void rotator() {
        // Create a backup directory
        File dir = new File(this.configPath);
        Path pdir = dir.toPath();
        Path parent = pdir.getParent();
        Path backupDir = Paths.get(parent.toString(), "backup");
        if (!backupDir.toFile().exists())
            backupDir.toFile().mkdirs();

        // Create a timestamped file xml-config-<timestamp>.xml in backup from xml-config.xml
        LocalDateTime now = LocalDateTime.now();
        String timestamp = "xml-config-%s-%s-%d-%d-%d-%d.xml";
        timestamp = String.format(timestamp, now.getMonth().toString(), now.getDayOfMonth(), now.getYear(),
                now.getHour(), now.getMinute(), now.getSecond());
        Path backup = Paths.get(backupDir.toString(), timestamp);
        if (backup.toFile().exists())
            logger.error("%s already exists.  Overwriting", backup.toString());
        try {
            Files.copy(pdir, backup);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Given an existing XML file for the Testsuite, edit it according to the params
     * @param tsPath
     */
    public void editTestSuite(String tsPath, String newpath) {
        File xunit = new File(tsPath);
        if (!xunit.exists())
            throw new InvalidArgument(tsPath + " does not exist");
        JAXBHelper jaxb = new JAXBHelper();
        Optional<Testsuites> ts = IJAXBHelper.unmarshaller(Testsuites.class, xunit,
                jaxb.getXSDFromResource(Testsuites.class));
        if (!ts.isPresent())
            throw new XMLUnmarshallError();

        Testsuites suites = ts.get();
        List<com.github.redhatqe.polarize.importer.xunit.Property> props = suites.getProperties().getProperty();
        // first, modify all props with same name
        props.forEach(p -> {
            String name = p.getName();
            TestsuiteProps prop = TestsuiteProps.fromString(name);
            switch (prop) {
                case DRY_RUN:
                    Boolean dryrun = this.getTestrunDryRun();
                    if (dryrun != null)
                        p.setValue(dryrun.toString());
                    break;
                case TEMPLATE_ID:
                    String id = this.getTemplateId();
                    if (id != null)
                        p.setValue(id);
                    break;
                case TESTRUN_FINISHED:
                    Boolean finished = this.getTestrunSetFinished();
                    if (finished != null)
                        p.setValue(this.getTestrunSetFinished().toString());
                    break;
                case USER:
                    this.logger.error("Need to add user to config");
                    break;
                case PROJECT:
                    String project = this.getProject();
                    if (project != null)
                        p.setValue(project);
                    break;
                case INCLUDE_SKIPPED:
                    Boolean include = this.getTestrunIncludeSkipped();
                    if (include != null)
                        p.setValue(include.toString());
                    break;
                case RESPONSE:
                    String selname = this.getXunitSelectorName();
                    String selval = this.getXunitSelectorVal();
                    if (selname != null && selval != null) {
                        p.setValue(selval);
                        p.setName("polarion-reponse-" + selname);
                    }
                    break;
                case TESTRUN_ID:
                    this.logger.error("Need to add testrun-id to config");
                    break;
                case TESTRUN_TITLE:
                    String title = this.getTestrunTitle();
                    if (title != null)
                        p.setValue(title);
                    break;
                default:
                    // That means we have a custom field
                    break;
            }
        });
        List<Property> newprops = new ArrayList<>();
        for(Property p: this.customProps) {
            boolean matched = false;
            for(Property p2: props) {
                matched = p.getName().equals(p2.getName());
                boolean found = false;
                if (matched) {
                    String val = p2.getValue();
                    p.setValue(val);
                    String name = p.getName();
                    break;
                }
            }
            if (!matched)
                newprops.add(p);
        }
        props.addAll(newprops);

        File newxunit = new File(newpath);
        IJAXBHelper.marshaller(suites ,newxunit, jaxb.getXSDFromResource(Testsuites.class));
    }

    public static void main(String[] args) {
        Configurator cfg = new Configurator();
        cfg.parse(args);
        String path = cfg.configPath;
        cfg.editTestSuite("/tmp/testng-polarion.xml", "/tmp/modified-polarion.xml");
        cfg.rotator();
        Configurator.writeOut(path, cfg.cfg);
    }

    public String getTestrunTitle() {
        return testrunTitle;
    }

    public void setTestrunTitle(String testrunTitle) {
        this.testrunTitle = testrunTitle;
    }

    public String getProject() {
        return project;
    }

    public void setProject(String project) {
        this.project = project;
    }

    public String getTestcasePrefix() {
        return testcasePrefix;
    }

    public void setTestcasePrefix(String testcasePrefix) {
        this.testcasePrefix = testcasePrefix;
    }

    public String getTestcaseSuffix() {
        return testcaseSuffix;
    }

    public void setTestcaseSuffix(String testcaseSuffix) {
        this.testcaseSuffix = testcaseSuffix;
    }

    public String getPlannedin() {
        return plannedin;
    }

    public void setPlannedin(String plannedin) {
        this.plannedin = plannedin;
    }

    public String getJenkinsjobs() {
        return jenkinsjobs;
    }

    public void setJenkinsjobs(String jenkinsjobs) {
        this.jenkinsjobs = jenkinsjobs;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getTemplateId() {
        return templateId;
    }

    public void setTemplateId(String templateId) {
        this.templateId = templateId;
    }

    public Boolean getTestcaseImporterEnabled() {
        return testcaseImporterEnabled;
    }

    public void setTestcaseImporterEnabled(Boolean testcaseImporterEnabled) {
        this.testcaseImporterEnabled = testcaseImporterEnabled;
    }

    public Boolean getXunitImporterEnabled() {
        return xunitImporterEnabled;
    }

    public void setXunitImporterEnabled(Boolean xunitImporterEnabled) {
        this.xunitImporterEnabled = xunitImporterEnabled;
    }

    public Boolean getTestrunDryRun() {
        return testrunDryRun;
    }

    public void setTestrunDryRun(Boolean testrunDryRun) {
        this.testrunDryRun = testrunDryRun;
    }

    public Boolean getTestrunSetFinished() {
        return testrunSetFinished;
    }

    public void setTestrunSetFinished(Boolean testrunSetFinished) {
        this.testrunSetFinished = testrunSetFinished;
    }

    public Boolean getTestrunIncludeSkipped() {
        return testrunIncludeSkipped;
    }

    public void setTestrunIncludeSkipped(Boolean testrunIncludeSkipped) {
        this.testrunIncludeSkipped = testrunIncludeSkipped;
    }

    public Integer getTestcaseTimeout() {
        return testcaseTimeout;
    }

    public void setTestcaseTimeout(Integer testcaseTimeout) {
        this.testcaseTimeout = testcaseTimeout;
    }

    public Integer getXunitTimeout() {
        return xunitTimeout;
    }

    public void setXunitTimeout(Integer xunitTimeout) {
        this.xunitTimeout = xunitTimeout;
    }

    public String getTcSelectorName() {
        return tcSelectorName;
    }

    public void setTcSelectorName(String tcSelectorName) {
        this.tcSelectorName = tcSelectorName;
    }

    public String getTcSelectorVal() {
        return tcSelectorVal;
    }

    public void setTcSelectorVal(String tcSelectorVal) {
        this.tcSelectorVal = tcSelectorVal;
    }

    public String getXunitSelectorName() {
        return xunitSelectorName;
    }

    public void setXunitSelectorName(String xunitSelectorName) {
        this.xunitSelectorName = xunitSelectorName;
    }

    public String getXunitSelectorVal() {
        return xunitSelectorVal;
    }

    public void setXunitSelectorVal(String xunitSelectorVal) {
        this.xunitSelectorVal = xunitSelectorVal;
    }

    @Override
    public URL getXSDFromResource(Class<?> t) {
        URL xsd;
        if (t == ConfigType.class) {
            xsd = JAXBHelper.class.getClass().getResource("configuration/xml-config.xsd");
        }
        else
            throw new XSDValidationError();
        return xsd;
    }

    interface Getter<T> {
        T get();
    }

    interface Setter<T> {
        void set(T t);
    }
}
