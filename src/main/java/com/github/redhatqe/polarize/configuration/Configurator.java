package com.github.redhatqe.polarize.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.redhatqe.polarize.IJAXBHelper;
import com.github.redhatqe.polarize.JAXBHelper;
import com.github.redhatqe.polarize.Utility;
import com.github.redhatqe.polarize.exceptions.ConfigurationError;
import com.github.redhatqe.polarize.exceptions.InvalidArgument;
import com.github.redhatqe.polarize.exceptions.XMLUnmarshallError;
import com.github.redhatqe.polarize.exceptions.XSDValidationError;

import com.github.redhatqe.polarize.importer.ImporterRequest;
import com.github.redhatqe.polarize.importer.xunit.*;
import com.github.redhatqe.polarize.utils.Tuple;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Class that defines an API to edit the xml-config.xml file
 */
public class Configurator implements IJAXBHelper {
    public static Logger logger = LoggerFactory.getLogger(Configurator.class);
    public OptionParser parser;
    public XMLConfig config;
    public ConfigType cfg;
    public String configPath;

    private String testrunTitle;
    private String testrunID;
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
    private String newXunit;
    private String currentXunit;

    private Boolean testcaseImporterEnabled;
    private Boolean xunitImporterEnabled;
    private Boolean testrunDryRun;
    private Boolean testrunSetFinished;
    private Boolean testrunIncludeSkipped;
    private Boolean editConfig;

    private Integer testcaseTimeout;
    private Integer xunitTimeout;

    public String tcPath;
    public List<ServerType> servers;
    private String groupId;
    private String projectName;

    private Map<String, Tuple<Getter<String>, Setter<String>>> sOptToAccessors = new HashMap<>();
    private Map<String, Tuple<Getter<Boolean>, Setter<Boolean>>> bOptToAccessors = new HashMap<>();
    private Map<String, Tuple<Getter<Integer>,Setter<Integer>>> iOptToAccessors = new HashMap<>();
    private Map<String, OptionSpec<String>> sSpecs = new HashMap<>();
    private Map<String, OptionSpec<Boolean>> bSpecs = new HashMap<>();
    private Map<String, OptionSpec<Integer>> iSpecs = new HashMap<>();
    private Map<String, String> testsuiteProps = new HashMap<>();
    private List<Property> customProps;
    private List<PropertyType> tsProperties = new ArrayList<>();

    private OptionSet opts;

    private void init(XMLConfig cfg) {
        this.configPath = java.lang.System.getProperty("user.home") + "/.polarize/xml-config.xml";
        this.config = cfg;
        this.cfg = this.config.config;
        this.parser = new OptionParser();
        this.servers = new ArrayList<>();
        this.configureParser();
    }

    public Configurator() {
        this.init(new XMLConfig(null));
    }

    public Configurator(String configPath) {
        this.init(new XMLConfig(new File(configPath)));
    }

    private void configureParser() {
        sOptToAccessors.put(Opts.TESTRUN_TITLE, new Tuple<>(this::getTestrunTitle, this::setTestrunTitle));
        sOptToAccessors.put(Opts.TESTRUN_ID, new Tuple<>(this::getTestrunID, this::setTestrunID));
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
        sOptToAccessors.put(Opts.NEW_XUNIT, new Tuple<>(this::getNewXunit, this::setNewXunit));
        sOptToAccessors.put(Opts.CURRENT_XUNIT, new Tuple<>(this::getCurrentXunit, this::setCurrentXunit));
        sOptToAccessors.put(Opts.PROJECT_NAME, new Tuple<>(this::getProjectName, this::setProjectName));
        sOptToAccessors.put(Opts.GROUP_ID, new Tuple<>(this::getGroupId, this::setGroupId));

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
        bSpecs.put(Opts.EDIT_CONFIG, this.parser.accepts(Opts.EDIT_CONFIG).withOptionalArg().ofType(Boolean.class)
        .defaultsTo(Boolean.FALSE));
    }

    /**
     *
     * @param args
     */
    private void parse(String[] args){
        OptionSet opts = parser.parse(args);
        this.opts = opts;
        this.sOptToAccessors.entrySet().forEach(es -> {
            String opt = es.getKey();
            OptionSpec<String> spec = this.sSpecs.get(opt);
            if (this.opts.has(spec)) {
                String val = opts.valueOf(spec);
                if (val.contains("&quot;"))
                    val = val.replace("&quot;", "");
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
        this.editConfig = opts.has(this.bSpecs.get(Opts.EDIT_CONFIG));

        // Get all the --property options
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

    private void setConfigType() {
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
                    logger.error(String.format("Unknown property name %s", name));
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
                case "template-id":
                    String template = this.getTemplateId();
                    if (!template.equals(""))
                        p.setVal(template);
                    break;
                default:
                    logger.error("Unknown property name %s", name);
            }
        }
    }


    private void setTCImporter(ImporterType imp) {
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

    private void setXunitImporter(ImporterType imp) {
        List<PropertyType> customProps = imp.getCustomFields().getProperty();
        this.setCustomProperties(customProps);

        List<PropertyType> tsProps = imp.getTestSuite().getProperty();
        List<PropertyType> fromConfig = new ArrayList<>();
        if (this.opts.has(iSpecs.get(Opts.XUNIT_IMPORTER_TIMEOUT)))
            imp.getTimeout().setMillis(this.getXunitTimeout().toString());
        if (this.opts.has(bSpecs.get(Opts.XUNIT_IMPORTER_ENABLED)))
            imp.setEnabled(this.getXunitImporterEnabled());
        //if (this.opts.has(sSpecs.get(Opts.TESTRUN_TITLE)))
        this.setTestRunTitleFromConfig(imp, fromConfig);
        this.setTestRunIDFromConfig(imp, fromConfig);
        if (this.opts.has(sSpecs.get(Opts.TEMPLATE_ID)))
            this.setTemplateIDFromConfig(imp, fromConfig);
        if (this.opts.has(bSpecs.get(Opts.TR_DRY_RUN)))
            this.setTestRunProperty(bSpecs.get(Opts.TR_DRY_RUN), Opts.TR_DRY_RUN, fromConfig);
        if (this.opts.has(bSpecs.get(Opts.TR_INCLUDE_SKIPPED)))
            this.setTestRunProperty(bSpecs.get(Opts.TR_INCLUDE_SKIPPED), Opts.TR_INCLUDE_SKIPPED, fromConfig);
        if (this.opts.has(bSpecs.get(Opts.TR_SET_FINISHED)))
            this.setTestRunProperty(bSpecs.get(Opts.TR_SET_FINISHED), Opts.TR_SET_FINISHED, fromConfig);

        // CLI config overrides XML
        Set<String> pts = new HashSet<>();
        for (PropertyType pt: fromConfig) {
            pts.add(pt.getName());
        }
        for (PropertyType pt: tsProps) {
            if (!pts.contains(pt.getName()))
                fromConfig.add(pt);
        }
        this.setTestRunProperties(fromConfig);
        this.tsProperties = fromConfig;
        this.setSelectorName(imp, Opts.XUNIT_SELECTOR_NAME, Opts.XUNIT_SELECTOR_VAL);
    }

    private <T> void setTestRunProperty(OptionSpec<T> opt, String propName, List<PropertyType> added) {
        T val = this.opts.valueOf(opt);
        PropertyType pt = new PropertyType();
        pt.setName(propName);
        pt.setVal(val.toString());
        added.add(pt);

        // This is pretty ugly and hacky.  Might be better to do this in the getter
        switch(propName) {
            case "dry-run":
                this.setTestrunDryRun((Boolean) val);
                break;
            case "include-skipped":
                this.setTestrunIncludeSkipped((Boolean) val);
                break;
            case "set-testrun-finished":
                this.setTestrunSetFinished((Boolean) val);
                break;
            default:
                logger.error(String.format("Unknown property %s", propName));
        }
    }

    /**
     * FIXME: This is ugly too.  Move this into the constructor or getter
     *
     * @param imp
     * @param added
     */
    private void setTemplateIDFromConfig(ImporterType imp, List<PropertyType> added) {
        String templateId = this.opts.valueOf(sSpecs.get(Opts.TEMPLATE_ID));
        this.setTemplateId(templateId);
        TemplateIdType tid = imp.getTemplateId();
        tid.setId(this.getTemplateId());
        imp.setTemplateId(tid);
        PropertyType pt = new PropertyType();
        pt.setName("template-id");
        pt.setVal(templateId);
        added.add(pt);
    }

    /**
     * FIXME: This is ugly and hacky.  Should probably be doing this either in the constructor or the getter
     *
     * @param imp
     * @param added
     */
    private void setTestRunTitleFromConfig(ImporterType imp, List<PropertyType> added) {
        TestrunType tr = imp.getTestrun();
        String title = this.getTestrunTitle();

        tr.setTitle(title);
        imp.setTestrun(tr);
        PropertyType pt = new PropertyType();
        pt.setName("testrun-title");
        pt.setVal(this.getTestrunTitle());
        added.add(pt);
    }

    private void setTestRunIDFromConfig(ImporterType imp, List<PropertyType> added) {
        TestrunType tr = imp.getTestrun();
        String title = this.getTestrunID();

        tr.setTitle(title);
        imp.setTestrun(tr);
        PropertyType pt = new PropertyType();
        pt.setName("testrun-id");
        pt.setVal(title);
        added.add(pt);
    }

    private void setSelectorName(ImporterType imp, String name, String val) {
        SelectorType st = imp.getSelector();
        if (sSpecs.containsKey(name) && this.opts.has(sSpecs.get(name))) {
            String selname = this.opts.valueOf(sSpecs.get(name));
            st.setName(selname);
            if (imp.getType().equals("xunit"))
                this.setXunitSelectorName(selname);
            else
                this.setTcSelectorName(selname);
            imp.setSelector(st);
        }
        else {
            String n = (imp.getType().equals("xunit")) ? this.getXunitSelectorName() : this.getTcSelectorName();
            st.setName(n);
        }

        if (sSpecs.containsKey(val) && this.opts.has(sSpecs.get(val))) {
            String sval = this.opts.valueOf(this.sSpecs.get(val));
            st.setVal(sval);
            imp.setSelector(st);
            if (imp.getType().equals("testcase"))
                this.setTcSelectorVal(sval);
            else
                this.setXunitSelectorVal(sval);
        }
        else {
            String v = (imp.getType().equals("testcase")) ? this.getTcSelectorVal() : this.getXunitSelectorVal();
            st.setVal(v);
        }

    }


    private ServerType parseServer(String server) {
        ServerType st = new ServerType();

        String[] tokens = server.split(",");
        if (tokens[0].equals(""))
            throw new ConfigurationError("First entry in comma separated list must be the name of the server");
        st.setName(tokens[0]);
        st.setUser(tokens[1]);
        st.setPassword(tokens[2]);
        try {
            st.setUrl(tokens[3]);
        }
        catch (ArrayIndexOutOfBoundsException oob) {

        }
        return st;
    }

    /**
     * This is a string in the command line like --property plannedin=RHEL_7_3
     * @param cust
     */
    private Property parseProperty(String cust) {
        String[] tokens = cust.split("=");
        if (tokens.length != 2)
            throw new InvalidArgument("--property must be in key=value form");
        if (tokens[0].contains(" "))
                throw new InvalidArgument("--property in name=val can not have space in name");
        if (tokens[1].contains("\""))
            tokens[1] = tokens[1].replace("\"", "");
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

    private enum TestsuiteProps {
        USER("polarion-user-id"),
        PROJECT("polarion-project-id"),
        TESTRUN_FINISHED("polarion-set-testrun-finished"),
        DRY_RUN("polarion-dry-run"),
        INCLUDE_SKIPPED("polarion-include-skipped"),
        RESPONSE("polarion-response"),
        TESTRUN_TITLE("polarion-testrun-title"),
        TESTRUN_ID("polarion-testrun-id"),
        TEMPLATE_ID("polarion-template-id"),
        CUSTOM("polarion-custom");

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
                    else if (s.contains("custom"))
                        return CUSTOM;
                    else
                        throw new java.lang.IllegalArgumentException(String.format("No enum constant for %s", s));
            }
        }
    }

    /**
     * Backs up the original xml-config.xml
     */
    public static void rotator(String cfgPath) {
        File dir = new File(cfgPath);
        Path pdir = dir.toPath();
        Path parent = pdir.getParent();
        Path backupDir = Paths.get(parent.toString(), "backup");
        if (!backupDir.toFile().exists()) {
            Boolean success = backupDir.toFile().mkdirs();
            System.out.println(String.format("Creation of %s was successful: %s", backupDir.toString(),
                    success.toString()));
        }

        String timestamp = Utility.makeTimeStamp() + ".xml";
        Path backup = Paths.get(backupDir.toString(), timestamp);
        if (backup.toFile().exists())
            logger.error("%s already exists.  Overwriting", backup.toString());
        try {
            Files.copy(pdir, backup);
            logger.info("Original xml-config.xml was backed up as %s", backup);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void setTestPropsFromXML(com.github.redhatqe.polarize.importer.xunit.Property p) {
        TestsuiteProps prop = TestsuiteProps.fromString(p.getName());
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
                logger.warn("Need to add user to config");
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
                    p.setName("polarion-response-" + selname);
                }
                break;
            case TESTRUN_ID:
                String tid = this.getTestrunID();
                if (tid != null && !tid.equals(""))
                    p.setValue(tid);
                break;
            case TESTRUN_TITLE:
                String title = this.getTestrunTitle();
                if (title != null && !title.equals(""))
                    p.setValue(title);
                break;
            case CUSTOM:
                break;
            default:
                // That means we have a custom field
                break;
        }
    }

    /**
     * Check if a PropertyType in tsProperties matches any in the XML, overriding if they match adding if not
     *
     * @param props list of Property from the testsuite XML
     */
    private void matchProps(List<com.github.redhatqe.polarize.importer.xunit.Property> props) {
        List<com.github.redhatqe.polarize.importer.xunit.Property> added = new ArrayList<>();
        for(PropertyType pt: this.tsProperties) {
            Boolean matched = false;
            for(com.github.redhatqe.polarize.importer.xunit.Property p: props) {
                if (p.getName().contains(pt.getName())) {
                    p.setValue(pt.getVal());
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                com.github.redhatqe.polarize.importer.xunit.Property unmatched =
                        new com.github.redhatqe.polarize.importer.xunit.Property();
                unmatched.setName("polarion-" + pt.getName());
                unmatched.setValue(pt.getVal());
                added.add(unmatched);
            }
        }
        props.addAll(added);
    }

    /**
     * Given an existing XML file for the Testsuite, edit it according to the params
     *
     * @param tsPath
     */
    public void editTestSuite(String tsPath, String newpath) {
        File xunit = new File(tsPath);
        if (tsPath.startsWith("http"))
            xunit = ImporterRequest.download(tsPath, "/tmp/tmp-polarion.xml");
        if (!xunit.exists())
            throw new InvalidArgument(tsPath + " does not exist");
        JAXBHelper jaxb = new JAXBHelper();
        Optional<Testsuites> ts = IJAXBHelper.unmarshaller(Testsuites.class, xunit,
                jaxb.getXSDFromResource(Testsuites.class));
        if (!ts.isPresent())
            throw new XMLUnmarshallError();

        Testsuites suites = ts.get();
        List<com.github.redhatqe.polarize.importer.xunit.Property> props = suites.getProperties().getProperty();
        this.matchProps(props);
        props.forEach(this::setTestPropsFromXML);
        props.removeIf(p -> p.getValue().equals(""));

        List<Property> newprops = new ArrayList<>();
        for(Property p: this.customProps) {
            boolean matched = false;
            for(Property p2: props) {
                matched = p.getName().equals(p2.getName());
                if (matched) {
                    String val = p2.getValue();
                    p.setValue(val);
                    break;
                }
            }
            if (!matched)
                newprops.add(p);
        }
        props.addAll(newprops);

        File newxunit = new File(newpath);
        IJAXBHelper.marshaller(suites ,newxunit, jaxb.getXSDFromResource(Testsuites.class));

        ObjectMapper mapper = new ObjectMapper();
        try {
            mapper.writer().withDefaultPrettyPrinter().writeValue(new File("/tmp/testsuites.json"), suites);
            mapper.writer().withDefaultPrettyPrinter().writeValue(new File("/tmp/config.json"), this.cfg);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Program to edit xml-config.xml or a testng-polarion.xml file
     *
     * @param args
     */
    public static void main(String[] args) {
        Configurator cfg = new Configurator();
        cfg.parse(args);
        String path = cfg.configPath;
        String testng;
        OptionSpec<String> xunit = cfg.sSpecs.get(Opts.CURRENT_XUNIT);
        if (cfg.opts.has(xunit))
            testng = cfg.opts.valueOf(xunit);
        else
            testng = cfg.config.getXunitImporterFilePath();
        String newXunit = cfg.getNewXunit();
        cfg.editTestSuite(testng, newXunit);

        Configurator.parseJson("/home/stoner/.polarize/config.json");

        Boolean edit = cfg.getEditConfig();
        if (edit) {
            Configurator.rotator(path);
            Configurator.writeOut(path, cfg.cfg);
        }
        logger.info("Done configuring the config file");
    }

    public static void parseJson(String jsonPath) {
        Configurator cfg = new Configurator();
        Opts opts = new Opts();
        File json = new File(jsonPath);
        StringBuffer sb = new StringBuffer();
        try {
            BufferedReader rdr = Files.newBufferedReader(json.toPath());
            rdr.lines().forEach(l -> sb.append(l).append("\n"));
        } catch (IOException e) {
            e.printStackTrace();
        }
        String[] newArgs = opts.parse(sb.toString());
        cfg.parse(newArgs);

        OptionSpec<String> xunit = cfg.sSpecs.get(Opts.CURRENT_XUNIT);
        String testng;
        if (xunit != null && cfg.opts.has(xunit))
            testng = cfg.opts.valueOf(xunit);
        else
            testng = cfg.config.getXunitImporterFilePath();
        cfg.editTestSuite(testng, "/tmp/modified-from-json.xml");
    }

    public String sanitize(String in) {
        if (in.contains("&quot;"))
            return in.replace("&quot;", "");
        if (in.contains("\""))
            return in.replace("\"", "");
        return in;
    }

    public String getTestrunID() {
        if (this.testrunID == null) {
            this.testrunID = this.sanitize(this.config.xunit.getTestrun().getId());
            if (this.testrunID.equals("{project-name}"))
                this.testrunID = Utility.makeTimeStamp(this.cfg.getProjectName().getName(), "");
        }
        return testrunID;
    }

    public void setTestrunID(String id) {
        this.testrunID = this.sanitize(id);
    }

    public String getTestrunTitle() {
        if (this.testrunTitle == null) {
            this.testrunTitle = "";
        }
        return this.testrunTitle;
    }

    public void setTestrunTitle(String testrunTitle) {
        this.testrunTitle = this.sanitize(testrunTitle);
    }

    public String getProject() {
        if (this.project == null)
            this.project = this.sanitize(this.cfg.getProject());
        return project;
    }

    public void setProject(String project) {
        this.project = this.sanitize(project);
    }

    public String getTestcasePrefix() {
        if (this.testcasePrefix == null)
            this.testcasePrefix = this.config.testcase.getTitle().getPrefix();
        return testcasePrefix;
    }

    public void setTestcasePrefix(String testcasePrefix) {
        this.testcasePrefix = this.sanitize(testcasePrefix);
    }

    public String getTestcaseSuffix() {
        if (this.testcaseSuffix == null)
            this.testcaseSuffix = this.config.testcase.getTitle().getSuffix();
        return testcaseSuffix;
    }

    public void setTestcaseSuffix(String testcaseSuffix) {
        this.testcaseSuffix = this.sanitize(testcaseSuffix);
    }

    private String searchCustomFields(String field) {
        String value = "";
        for(PropertyType pt: this.config.getCustomFields()) {
            if (pt.getName().equals(field))
                value = this.sanitize(pt.getVal());
        }
        return value;
    }

    public String getPlannedin() {
        if (this.plannedin == null)
            this.plannedin = this.sanitize(this.searchCustomFields("plannedin"));
        return plannedin;
    }

    public void setPlannedin(String plannedin) {
        this.plannedin = this.sanitize(plannedin);
    }

    public String getJenkinsjobs() {
        if (this.jenkinsjobs == null)
            this.jenkinsjobs = this.searchCustomFields("jenkinsjobs");
        return jenkinsjobs;
    }

    public void setJenkinsjobs(String jenkinsjobs) {
        this.jenkinsjobs = this.sanitize(jenkinsjobs);
    }

    public String getNotes() {
        if (this.notes == null)
            this.notes = this.sanitize(this.searchCustomFields("notes"));
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = this.sanitize(notes);
    }

    public String getTemplateId() {
        return templateId;
    }

    public void setTemplateId(String templateId) {
        this.templateId = this.sanitize(templateId);
    }

    public Boolean getTestcaseImporterEnabled() {
        if (this.testcaseImporterEnabled == null)
            this.testcaseImporterEnabled = this.config.testcase.isEnabled();
        return testcaseImporterEnabled;
    }

    public void setTestcaseImporterEnabled(Boolean testcaseImporterEnabled) {
        this.testcaseImporterEnabled = testcaseImporterEnabled;
    }

    public Boolean getXunitImporterEnabled() {
        if (this.xunitImporterEnabled == null)
            this.xunitImporterEnabled = this.config.xunit.isEnabled();
        return xunitImporterEnabled;
    }

    public void setXunitImporterEnabled(Boolean xunitImporterEnabled) {
        this.xunitImporterEnabled = xunitImporterEnabled;
    }

    private Boolean searchTestSuiteProps(String field) {
        Boolean val = null;
        for(PropertyType pt: this.config.xunit.getTestSuite().getProperty()) {
            if (pt.getName().equals(field))
                val = Boolean.valueOf(pt.getVal());
        }
        return val;
    }

    public Boolean getTestrunDryRun() {
        if (this.testrunDryRun == null) {
            Boolean val = this.searchTestSuiteProps("dry-run");
            this.testrunDryRun = (val != null) ? val : false;
        }
        return testrunDryRun;
    }

    public void setTestrunDryRun(Boolean testrunDryRun) {
        this.testrunDryRun = testrunDryRun;
    }

    public Boolean getTestrunSetFinished() {
        if (this.testrunSetFinished == null) {
            Boolean val = this.searchTestSuiteProps("set-testrun-finished");
            this.testrunSetFinished = (val != null) ? val : true;
        }
        return testrunSetFinished;
    }

    public void setTestrunSetFinished(Boolean testrunSetFinished) {
        this.testrunSetFinished = testrunSetFinished;
    }

    public Boolean getTestrunIncludeSkipped() {
        if (this.testrunIncludeSkipped == null) {
            Boolean val = this.searchTestSuiteProps("include-skipped");
            this.testrunIncludeSkipped = (val != null) ? val : true;
        }
        return testrunIncludeSkipped;
    }

    public void setTestrunIncludeSkipped(Boolean testrunIncludeSkipped) {
        this.testrunIncludeSkipped = testrunIncludeSkipped;
    }

    public Integer getTestcaseTimeout() {
        if (this.testcaseTimeout == null)
            this.testcaseTimeout = Integer.valueOf(this.config.testcase.getTimeout().getMillis());
        return testcaseTimeout;
    }

    public void setTestcaseTimeout(Integer testcaseTimeout) {
        this.testcaseTimeout = testcaseTimeout;
    }

    public Integer getXunitTimeout() {
        if (this.xunitTimeout == null)
            this.xunitTimeout = Integer.valueOf(this.config.xunit.getTimeout().getMillis());
        return xunitTimeout;
    }

    public void setXunitTimeout(Integer xunitTimeout) {
        this.xunitTimeout = xunitTimeout;
    }

    public String getTcSelectorName() {
        if (this.tcSelectorName == null)
            this.tcSelectorName = this.sanitize(this.config.testcase.getSelector().getName());
        return tcSelectorName;
    }

    public void setTcSelectorName(String tcSelectorName) {
        this.tcSelectorName = this.sanitize(tcSelectorName);
    }

    public String getTcSelectorVal() {
        if (this.tcSelectorVal == null)
            this.tcSelectorVal = this.sanitize(this.config.testcase.getSelector().getVal());
        return tcSelectorVal;
    }

    public void setTcSelectorVal(String tcSelectorVal) {
        this.tcSelectorVal = this.sanitize(tcSelectorVal);
    }

    public String getXunitSelectorName() {
        if (this.xunitSelectorName == null)
            this.xunitSelectorName = this.sanitize(this.config.xunit.getSelector().getName());
        return xunitSelectorName;
    }

    public void setXunitSelectorName(String xunitSelectorName) {
        this.xunitSelectorName = this.sanitize(xunitSelectorName);
    }

    public String getXunitSelectorVal() {
        if (this.xunitSelectorVal == null)
            this.xunitSelectorVal = this.config.xunit.getSelector().getVal();
        return xunitSelectorVal;
    }

    public void setXunitSelectorVal(String xunitSelectorVal) {
        this.xunitSelectorVal = this.sanitize(xunitSelectorVal);
    }

    public String getNewXunit() {
        if (this.newXunit == null)
            this.newXunit = "";
        return newXunit;
    }

    public void setNewXunit(String newXunit) {
        this.newXunit = this.sanitize(newXunit);
    }

    public String getCurrentXunit() {
        if (this.currentXunit == null)
            this.currentXunit = this.sanitize(this.config.xunit.getFile().getPath());
        return currentXunit;
    }

    public void setCurrentXunit(String currentXunit) {
        this.currentXunit = this.sanitize(currentXunit);
    }

    public Boolean getEditConfig() {
        if (this.editConfig == null)
            this.editConfig = false;
        return editConfig;
    }

    public void setEditConfig(Boolean editConfig) {
        this.editConfig = editConfig;
    }

    /**
     * This is a CLI only arg
     * @return
     */
    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getProjectName() {
        if (this.projectName == null)
            this.projectName = this.cfg.getProjectName().getName();
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
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
