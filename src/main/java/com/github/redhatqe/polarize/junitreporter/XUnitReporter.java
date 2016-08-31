package com.github.redhatqe.polarize.junitreporter;

import com.github.redhatqe.polarize.Configurator;
import com.github.redhatqe.polarize.IJAXBHelper;
import com.github.redhatqe.polarize.exceptions.XMLDescriptionError;
import com.github.redhatqe.polarize.exceptions.XMLUnmarshallError;
import com.github.redhatqe.polarize.importer.ImporterRequest;
import com.github.redhatqe.polarize.importer.xunit.Property;
import com.github.redhatqe.polarize.importer.xunit.Testcase;
import com.github.redhatqe.polarize.importer.xunit.Testsuite;
import com.github.redhatqe.polarize.importer.xunit.Testsuites;
import com.github.redhatqe.polarize.metadata.Requirement;
import com.github.redhatqe.polarize.metadata.TestDefinition;
import com.github.redhatqe.polarize.schema.WorkItem;
import org.testng.*;
import org.testng.xml.XmlSuite;
import org.testng.xml.XmlTest;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Class that handles junit report generation for TestNG
 *
 * Use this class when running TestNG tests as -reporter XUnitReporter.  It can be
 * configured through the reporter.properties file.  A default configuration is contained in the resources folder, but
 * a global environment variable of XUNIT_IMPORTER_CONFIG can also be set.  If this env var exists and it points to a
 * file, this file will be loaded instead.
 */
public class XUnitReporter implements IReporter {
    private final ReporterConfig config;
    private Map<String, String> polarizeConfig = com.github.redhatqe.polarize.Configurator.loadConfiguration();
    private final static File defaultPropertyFile =
            new File(System.getProperty("user.home") + "/.polarize/reporter.properties");

    public XUnitReporter() {
        this.config = XUnitReporter.configure();
    }

    public static ReporterConfig configure() {
        Properties props = XUnitReporter.getProperties();

        ReporterConfig config = new ReporterConfig();
        try {
            config.setAuthor(XUnitReporter.validateNonEmptyProperty("author-id", props));
            config.setProjectID(XUnitReporter.validateNonEmptyProperty("project-id", props));
        } catch (KeyException e) {
            e.printStackTrace();
        }

        config.setDryRun(Boolean.valueOf(props.getProperty("dry-run")));
        config.setIncludeSkipped(Boolean.valueOf(props.getProperty("include-skipped")));
        config.setResponseName(props.getProperty("response-name"));
        config.setSetTestRunFinished(Boolean.valueOf(props.getProperty("set-testrun-finished")));
        config.setTestrunID(props.getProperty("testrun-id"));
        config.setTestrunTitle(props.getProperty("testrun-title"));
        config.setTestcasesXMLPath(props.getProperty("testcases-xml-path"));
        config.setRequirementsXMLPath(props.getProperty("requirements-xml-path"));

        String fields = props.getProperty("custom-fields");
        config.setCustomFields(new HashMap<>());
        if (!fields.equals("")) {
            String[] flds = fields.split(",");
            Arrays.stream(flds)
                    .forEach(f -> {
                        String[] kvs = f.split("=");
                        config.setCustomFields(kvs[0], kvs[1]);
                    });
        }

        return config;
    }

    public static Properties getProperties() {
        Properties props = new Properties();
        Map<String, String> envs = System.getenv();
        if (envs.containsKey("XUNIT_IMPORTER_CONFIG")) {
            String path = envs.get("XUNIT_IMPORTER_CONFIG");
            File fpath = new File(path);
            if (fpath.exists()) {
                try {
                    FileInputStream fis = new FileInputStream(fpath);
                    props.load(fis);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        else if (XUnitReporter.defaultPropertyFile.exists()){
            try {
                FileInputStream fis = new FileInputStream(XUnitReporter.defaultPropertyFile);
                props.load(fis);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        else {
            InputStream is = XUnitReporter.class.getClassLoader().getResourceAsStream("reporter.properties");
            try {
                props.load(is);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return props;
    }


    private static String validateNonEmptyProperty(String key, Properties props) throws KeyException {
        if (!props.containsKey(key)) {
            throw new KeyException(String.format("Missing %s in reporter.properties", key));
        }

        String val = props.getProperty(key);
        if (val.equals(""))
            throw new KeyException(String.format("%s can not be empty string", val));
        return val;
    }

    /**
     * Generates a modified xunit result that can be used for the XUnit Importer
     *
     * Example of a modified junit file:
     *
     * <testsuites>
     *
     *     <testsuite>
     *         <testcase>
     *
     *         </testcase>
     *     </testsuite>
     * </testsuites>
     *
     * @param xmlSuites passed by TestNG
     * @param suites passed by TestNG
     * @param outputDirectory passed by TestNG.  configurable?
     */
    @Override
    public void generateReport(List<XmlSuite> xmlSuites, List<ISuite> suites, String outputDirectory) {
        Testsuites tsuites = XUnitReporter.getTestSuiteInfo(this);
        List<Testsuite> tsuite = tsuites.getTestsuite();

        // Get information for each <testsuite>
        suites.forEach(suite -> {
            String name = suite.getName();
            Map<String, ISuiteResult> results = suite.getResults();
            List<Testsuite> collected = results.entrySet().stream()
                    .map(es -> {
                        Testsuite ts = new Testsuite();
                        String key = es.getKey();
                        ISuiteResult result = es.getValue();
                        ITestContext ctx = result.getTestContext();

                        ts.setName(key);
                        ts.setErrors(Integer.toString(ctx.getFailedTests().size()));
                        ts.setSkipped(Integer.toString(ctx.getSkippedTests().size()));
                        ts.setTests(Integer.toString(ctx.getAllTestMethods().length));
                        Date start = ctx.getStartDate();
                        Date end = ctx.getEndDate();
                        float duration = (end.getTime() - start.getTime())/ 1000;
                        ts.setTime(Float.toString(duration));

                        List<Testcase> tests = ts.getTestcase();
                        List<Testcase> after = XUnitReporter.getMethodInfo(this, suite, tests);
                        return ts;
                    })
                    .collect(Collectors.toList());
            tsuite.addAll(collected);
        });

        // Now that we've gone through the suites, let's marshall this into an XML file for the XUnit Importer
        File reportPath = new File(outputDirectory + "/testng-polarion.xml");
        String url = this.polarizeConfig.get("polarion.url") + this.polarizeConfig.get("importer.xunit.endpoint");
        ImporterRequest.request(tsuites, Testsuites.class, url, reportPath.toString());
    }

    public static List<Testcase> getMethodInfo(XUnitReporter xUnitReporter, ISuite suite, List<Testcase> testcases) {
        // Get information for each <testcase>
        List<IInvokedMethod> invoked = suite.getAllInvokedMethods();
        for(IInvokedMethod meth: invoked) {
            Testcase testcase = new Testcase();
            testcases.add(testcase);

            ITestNGMethod fn = meth.getTestMethod();
            XmlTest test = fn.getXmlTest();
            ITestResult result = meth.getTestResult();
            result.getStartMillis();

            ITestClass clz = fn.getTestClass();
            String methname = fn.getMethodName();
            String classname = clz.getName();

            testcase.setName(methname);
            testcase.setClassname(classname);

            // Create the <properties> element, and all the child <property> sub-elements
            com.github.redhatqe.polarize.importer.xunit.Properties props =
                    new com.github.redhatqe.polarize.importer.xunit.Properties();
            List<Property> tcProps = props.getProperty();

            // Look up in the XML description file the qualifiedName to get the Polarion ID
            File xmlDesc = XUnitReporter.getXMLDescFile(xUnitReporter, classname, methname);
            String id = XUnitReporter.getPolarionIDFromXML(TestDefinition.class, xmlDesc);
            Property polarionID = XUnitReporter.createProperty("polarion-testcase-id", id);
            tcProps.add(polarionID);

            // Get all the iteration data
            Object[] params = result.getParameters();
            for(int x = 0; x < params.length; x++) {
                Property param = new Property();
                param.setName("polarion-parameter-" + Integer.toString(x));
                param.setValue(params[x].toString());
                tcProps.add(param);
            }
            testcase.setProperties(props);
        }
        return testcases;
    }

    public static Testsuites getTestSuiteInfo(XUnitReporter xUnitReporter) {
        Testsuites tsuites = new Testsuites();
        com.github.redhatqe.polarize.importer.xunit.Properties props =
                new com.github.redhatqe.polarize.importer.xunit.Properties();
        List<Property> properties = props.getProperty();

        Property author = XUnitReporter.createProperty("polarion-user-id", xUnitReporter.config.getAuthor());
        properties.add(author);

        Property projectID = XUnitReporter.createProperty("polarion-project-id", xUnitReporter.config.getProjectID());
        properties.add(projectID);

        Property testRunFinished = XUnitReporter.createProperty("polarion-set-testrun-finished",
                xUnitReporter.config.getSetTestRunFinished().toString());
        properties.add(testRunFinished);

        Property dryRun = XUnitReporter.createProperty("polarion-dry-run", xUnitReporter.config.getDryRun().toString());
        properties.add(dryRun);

        Property includeSkipped = XUnitReporter.createProperty("polarion-include-skipped",
                xUnitReporter.config.getIncludeSkipped().toString());
        properties.add(includeSkipped);

        Configurator cfg = XUnitReporter.createConditionalProperty(xUnitReporter, "polarion-response", "rhsm-qe", properties);
        cfg.setter();
        cfg = XUnitReporter.createConditionalProperty(xUnitReporter, "polarion-custom", null, properties);
        cfg.setter();
        cfg = XUnitReporter.createConditionalProperty(xUnitReporter, "polarion-testrun-title", null, properties);
        cfg.setter();
        cfg = XUnitReporter.createConditionalProperty(xUnitReporter, "polarion-testrun-id", null, properties);
        cfg.setter();

        tsuites.setProperties(props);
        return tsuites;
    }

    /**
     * Simple setter for a Property
     *
     * TODO: replace this with a lambda
     *
     * @param name
     * @param value
     * @return
     */
    private static Property createProperty(String name, String value) {
        Property prop = new Property();
        prop.setName(name);
        prop.setValue(value);
        return prop;
    }

    @FunctionalInterface
    interface Configurator {
        void setter();
    }

    private static Configurator
    createConditionalProperty(XUnitReporter xUnitReporter, String name, String value, List<Property> properties) {
        Configurator cfg;
        Property prop = new Property();
        prop.setName(name);

        switch(name) {
            case "polarion-testrun-title":
                cfg = () -> {
                    if (xUnitReporter.config.getTestrunTitle().equals(""))
                        return;
                    prop.setValue(xUnitReporter.config.getTestrunTitle());
                    properties.add(prop);
                };
                break;
            case "polarion-testrun-id":
                cfg = () -> {
                    if (xUnitReporter.config.getTestrunID().equals(""))
                        return;
                    prop.setValue(xUnitReporter.config.getTestrunID());
                    properties.add(prop);
                };
                break;
            case "polarion-response":
                cfg = () -> {
                    if (xUnitReporter.config.getResponseName().equals(""))
                        return;
                    prop.setName("polarion-response-" + value);
                    prop.setValue(xUnitReporter.config.getResponseName());
                    properties.add(prop);
                };
                break;
            case "polarion-custom":
                cfg = () -> {
                    Map<String, String> customFields = xUnitReporter.config.getCustomFields();
                    if (customFields.isEmpty())
                        return;
                    customFields.entrySet().forEach(entry -> {
                        String key = "polarion-custom-" + entry.getKey();
                        String val = entry.getValue();
                        Property p = new Property();
                        p.setName(key);
                        p.setValue(val);
                        properties.add(p);
                    });
                };
                break;
            default:
                cfg = null;
        }
        return cfg;
    }

    /**
     * Given the fully qualified method name, find the xml description file
     * @param xUnitReporter
     * @param className
     * @param methName
     */
    private static File getXMLDescFile(XUnitReporter xUnitReporter, String className, String methName) {
        String tcXMLPath = xUnitReporter.config.getTestcasesXMLPath();
        String projID = xUnitReporter.config.getProjectID();

        Path path = Paths.get(tcXMLPath, projID, className, methName + ".xml");
        File xmlDesc = path.toFile();
        if(!xmlDesc.exists())
            throw new XMLDescriptionError();

        return xmlDesc;
    }

    private static <T> String getPolarionIDFromXML(Class<T> t, File xmldesc) {
        JAXBReporter jaxb = new JAXBReporter();
        if (t == TestDefinition.class) {
            Optional<com.github.redhatqe.polarize.importer.testcase.Testcase> tc;
            tc = IJAXBHelper.unmarshaller(com.github.redhatqe.polarize.importer.testcase.Testcase.class, xmldesc,
                    jaxb.getXSDFromResource(com.github.redhatqe.polarize.importer.testcase.Testcase.class));
            if (!tc.isPresent())
                throw new XMLUnmarshallError();
            com.github.redhatqe.polarize.importer.testcase.Testcase tcase = tc.get();
            if (tcase.getUpdate() == null)
                return "";
            return tcase.getUpdate().getId();
        }
        else if (t == Requirement.class) {
            Optional<WorkItem> wi;
            wi = IJAXBHelper.unmarshaller(WorkItem.class, xmldesc, jaxb.getXSDFromResource(WorkItem.class));
            if (wi.isPresent())
                return wi.get().getRequirement().getId();
            else
                return "";
        }
        else
            throw new XMLDescriptionError();
    }
}
