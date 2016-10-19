package com.github.redhatqe.polarize.configuration;

import com.github.redhatqe.polarize.IJAXBHelper;
import com.github.redhatqe.polarize.JAXBHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.FileSystems;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Created by stoner on 10/17/16.
 */
public class XMLConfig {
    public ConfigType config;
    public Map<String, ImporterType> importers = new HashMap<>();
    public Map<String, String> customFields = new HashMap<>();
    public Map<String, String> testRunProps = new HashMap<>();
    public ServerType polarion;
    public ServerType kerb;
    public ServerType ossrh;
    public ImporterType xunit;
    public ImporterType testcase;
    public Logger logger = LoggerFactory.getLogger(XMLConfig.class);

    public XMLConfig(File path) {
        if (path == null) {
            String homeDir = System.getProperty("user.home");
            path = FileSystems.getDefault().getPath(homeDir + "/.polarize/xml-config.xml").toFile();
        }
        JAXBHelper jaxb = new JAXBHelper();
        Optional<ConfigType> maybeCfg;
        maybeCfg = IJAXBHelper.unmarshaller(ConfigType.class, path, jaxb.getXSDFromResource(ConfigType.class));
        if (!maybeCfg.isPresent()) {
            throw new Error("Could not load configuration file");
        }
        this.config = maybeCfg.get();
        this.initImporters();
        xunit = this.importers.get("xunit");
        testcase = this.importers.get("testcase");

        this.initCustomFields();
        this.initTestSuite();
        this.initServers();
    }

    private void initServers() {
        List<ServerType> servers = this.config.getServers().getServer();
        for(ServerType st: servers) {
            switch(st.getName()) {
                case "polarion":
                    this.polarion = st;
                    break;
                case "kerberos":
                    this.kerb = st;
                    break;
                case "ossrh":
                    this.ossrh = st;
                    break;
                default:
                    this.logger.error("Unknown server type: %s", st.getName());
            }
        }
    }

    private void initImporters() {
        List<ImporterType> importers = this.config.importers.getImporter();
        importers.forEach(i -> this.importers.put(i.type, i));
    }

    public List<PropertyType> getCustomFields() {
        CustomFieldsType fields = xunit.getCustomFields();
        return fields.getProperty();
    }

    /**
     * Given the customFields map, put this into the CustomFieldsType
     */
    public void setCustomFields() {
        CustomFieldsType cft = this.xunit.getCustomFields();
        List<PropertyType> props = cft.getProperty();
        props.clear();
        props.addAll(this.customFields.entrySet().stream()
                .map(es -> {
                    PropertyType pt = new PropertyType();
                    pt.setName(es.getKey());
                    pt.setVal(es.getValue());
                    return pt;
                })
                .collect(Collectors.toList()));
    }

    public void initCustomFields() {
        List<PropertyType> props = this.getCustomFields();
        props.forEach(p -> this.customFields.put(p.getName(), p.getVal()));
    }

    public List<PropertyType> getTestSuite() {
        TestSuiteType tProps = xunit.getTestSuite();
        return tProps.getProperty();
    }

    public void setTestSuite() {
        TestSuiteType tst = this.xunit.getTestSuite();
        List<PropertyType> props = tst.getProperty();
        props.clear();
        props.addAll(this.testRunProps.entrySet().stream()
                .map(es -> {
                    PropertyType pt = new PropertyType();
                    pt.setName(es.getKey());
                    pt.setVal(es.getValue());
                    return pt;
                })
                .collect(Collectors.toList()));
    }

    public void initTestSuite() {
        List<PropertyType> props = this.getTestSuite();
        props.forEach(p -> this.testRunProps.put(p.getName(), p.getVal()));
    }

    public static void main(String[] args) {
        XMLConfig xml = new XMLConfig(null);
    }
}
