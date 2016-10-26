package com.github.redhatqe.polarize.junitreporter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.github.redhatqe.polarize.FileHelper;
import com.github.redhatqe.polarize.IJAXBHelper;
import com.github.redhatqe.polarize.IdParams;
import com.github.redhatqe.polarize.JAXBHelper;
import com.github.redhatqe.polarize.configuration.ConfigType;
import com.github.redhatqe.polarize.configuration.XMLConfig;
import com.github.redhatqe.polarize.exceptions.MappingError;
import com.github.redhatqe.polarize.exceptions.RequirementAnnotationException;
import com.github.redhatqe.polarize.exceptions.XMLDescriptionError;
import com.github.redhatqe.polarize.exceptions.XMLUnmarshallError;
import com.github.redhatqe.polarize.importer.ImporterRequest;
import com.github.redhatqe.polarize.importer.xunit.*;
import com.github.redhatqe.polarize.importer.xunit.Error;
import com.github.redhatqe.polarize.metadata.Requirement;
import com.github.redhatqe.polarize.metadata.TestDefinition;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.*;
import org.testng.xml.XmlSuite;

import javax.jms.JMSException;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Class that handles junit report generation for TestNG
 *
 * Use this class when running TestNG tests as -reporter XUnitReporter.  It can be
 * configured through the xml-config.xml file.  A default configuration is contained in the resources folder, but
 * a global environment variable of XUNIT_IMPORTER_CONFIG can also be set.  If this env var exists and it points to a
 * file, this file will be loaded instead.
 */
public class XUnitReporter implements IReporter {
    private final static Logger logger = LoggerFactory.getLogger(XUnitReporter.class);
    private static XMLConfig config = new XMLConfig(null);
    private final static ConfigType cfg = config.config;
    private final static File defaultPropertyFile =
            new File(System.getProperty("user.home") + "/.polarize/reporter.properties");
    private static List<String> failedSuites = new ArrayList<>();

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


    /**
     * Generates a modified xunit result that can be used for the XUnit Importer
     *
     * Example of a modified junit file:
     *
     * @param xmlSuites passed by TestNG
     * @param suites passed by TestNG
     * @param outputDirectory passed by TestNG.  configurable?
     */
    @Override
    public void generateReport(List<XmlSuite> xmlSuites, List<ISuite> suites, String outputDirectory) {
        Testsuites tsuites = XUnitReporter.getTestSuiteInfo(config.xunit.getSelector().getName());
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
                        ts.setFailures(Integer.toString(ctx.getFailedTests().size()));
                        ts.setSkipped(Integer.toString(ctx.getSkippedTests().size()));
                        ts.setTests(Integer.toString(ctx.getAllTestMethods().length));
                        Date start = ctx.getStartDate();
                        Date end = ctx.getEndDate();
                        float duration = (end.getTime() - start.getTime())/ 1000;
                        ts.setTime(Float.toString(duration));

                        List<Testcase> tests = ts.getTestcase();
                        List<Testcase> after = XUnitReporter.getMethodInfo(suite, tests);
                        return ts;
                    })
                    .collect(Collectors.toList());
            tsuite.addAll(collected);
        });

        // Now that we've gone through the suites, let's marshall this into an XML file for the XUnit Importer
        File reportPath = new File(outputDirectory + "/testng-polarion.xml");
        JAXBHelper jaxb = new JAXBHelper();
        IJAXBHelper.marshaller(tsuites, reportPath, jaxb.getXSDFromResource(Testsuites.class));
    }

    /**
     * Makes an Xunit importer REST call
     * </p>
     * The actual response will come over the CI Message bus, not in the body of the http response.  Note that the
     * pw is sent over basic auth and is therefore not encrypted.
     *
     * @param url The URL endpoint for the REST call
     * @param user User name to authenticate as
     * @param pw The password for the user
     * @param reportPath path to where the xml file for uploading will be marshalled to
     * @param tsuites the object that will be marshalled into XML
     */
    public static void sendXunitImportRequest(String url, String user, String pw, File reportPath, Testsuites tsuites) {
        // Now that we've gone through the suites, let's marshall this into an XML file for the XUnit Importer
        CloseableHttpResponse resp =
                ImporterRequest.post(tsuites, Testsuites.class, url, reportPath.toString(), user, pw);
        HttpEntity entity = resp.getEntity();
        try {
            BufferedReader bfr = new BufferedReader(new InputStreamReader(entity.getContent()));
            System.out.println(bfr.lines().collect(Collectors.joining("\n")));
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println(resp.toString());
    }

    /**
     * A function factory that returns a Consumer type function usable to determine success of XUnit import request
     *
     * @return a Consumer function
     */
    public static Consumer<Optional<ObjectNode>> xunitMessageHandler() {
        return (node) -> {
            if (!node.isPresent()) {
                logger.warn("No ObjectNode received from the Message");
            } else {
                // TODO: figure out if the xunit import was successful.  If it wasn't, what do we do?  Retry?  Perhaps,
                // depending on the exception type, we might be able to retry.  Finding the log when we get an exception
                // will be very difficult
                JsonNode root = node.get().get("root");
                if (root.get("status").textValue().equals("passed")) {
                    logger.info("XUnit importer was successful");
                    logger.info(root.get("testrun-url").textValue());
                } else {
                    // Figure out which one failed
                    if (root.has("import-results")) {
                        JsonNode results = root.get("import-results");
                        results.elements().forEachRemaining(element -> {
                            if (element.has("status") && !element.get("status").textValue().equals("passed")) {
                                if (element.has("suite-name")) {
                                    String suite = element.get("suite-name").textValue();
                                    logger.info(suite + " failed to be updated");
                                    XUnitReporter.failedSuites.add(suite);
                                }
                            }
                        });
                    }
                    else
                        logger.error(root.get("message").asText());
                }
            }
        };
    }

    /**
     * Sets the status for a Testcase object given values from ITestResult
     * 
     * @param result
     * @param tc
     */
    private static void getStatus(ITestResult result, Testcase tc) {
        Throwable t = result.getThrowable();
        int status = result.getStatus();
        switch(status) {
            // Unfortunately, TestNG doesn't distinguish between an assertion failure and an error.  The way to check
            // is if getThrowable() returns non-null
            case ITestResult.FAILURE:
                if (t != null && !(t instanceof java.lang.AssertionError)) {
                    Error err = new Error();
                    err.setMessage(t.getMessage().substring(128));
                    err.setContent(t.getMessage());
                    tc.getError().add(err);
                }
                else {
                    Failure fail = new Failure();
                    if (t != null)
                        fail.setContent(t.getMessage());
                    tc.getFailure().add(fail);
                }
                break;
            case ITestResult.SKIP:
                tc.setSkipped("true");
                break;
            case ITestResult.SUCCESS:
                tc.setStatus("success");
                break;
            default:
                if (t != null) {
                    Error err = new Error();
                    err.setMessage(t.getMessage().substring(128));
                    err.setContent(t.getMessage());
                    tc.getError().add(err);
                }
                break;
        }
    }

    /**
     * Gets information from each invoked method in the test suite
     *
     * @param suite suite that was run by TestNG
     * @param testcases all the Testcase objects
     * @return modified list of the Testcase objects
     */
    private static List<Testcase> getMethodInfo(ISuite suite, List<Testcase> testcases) {
        List<IInvokedMethod> invoked = suite.getAllInvokedMethods();
        for(IInvokedMethod meth: invoked) {
            // FIXME: Need to figure out if this was a non @Test method
            ITestNGMethod fn = meth.getTestMethod();
            if (!fn.isTest()) {
                XUnitReporter.logger.info(String.format("Skipping non-test method %s", fn.getMethodName()));
                continue;
            }

            Testcase testcase = new Testcase();
            testcases.add(testcase);

            //XmlTest test = fn.getXmlTest();
            ITestResult result = meth.getTestResult();
            Long millis = result.getEndMillis() - result.getStartMillis();
            testcase.setTime(millis.toString());

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
            File xmlDesc = XUnitReporter.getXMLDescFile(classname, methname);
            String id = XUnitReporter.getPolarionIDFromXML(TestDefinition.class, xmlDesc);
            Property polarionID = XUnitReporter.createProperty("polarion-testcase-id", id);
            tcProps.add(polarionID);

            XUnitReporter.getStatus(result, testcase);

            // Load the mapping file
            String project = XUnitReporter.cfg.getProject();
            String path = XUnitReporter.cfg.getMapping().getPath();
            File fpath = new File(path);
            if (!fpath.exists()) {
                XUnitReporter.logger.error(String.format("Could not find mapping file %s", path));
                throw new MappingError();
            }
            String qual = String.format("%s.%s", classname, methname);
            Map<String, Map<String, IdParams>> mapping = FileHelper.loadMapping(fpath);
            Map<String, IdParams> inner = mapping.get(qual);
            IdParams ip = inner.get(project);
            List<String> args = ip.getParameters();

            // Get all the iteration data
            Object[] params = result.getParameters();
            if (args.size() != params.length) {
                XUnitReporter.logger.error("Length of parameters from IResult not the same as from mapping file");
                throw new MappingError();
            }
            for(int x = 0; x < params.length; x++) {
                Property param = new Property();
                param.setName("polarion-parameter-" + args.get(x));
                param.setValue(params[x].toString());
                tcProps.add(param);
            }
            testcase.setProperties(props);
        }
        return testcases;
    }

    private static Testsuites getTestSuiteInfo(String responseName) {
        Testsuites tsuites = new Testsuites();
        com.github.redhatqe.polarize.importer.xunit.Properties props =
                new com.github.redhatqe.polarize.importer.xunit.Properties();
        List<Property> properties = props.getProperty();

        Property author = XUnitReporter.createProperty("polarion-user-id", cfg.getAuthor());
        properties.add(author);

        Property projectID = XUnitReporter.createProperty("polarion-project-id", cfg.getProject());
        properties.add(projectID);

        Property testRunFinished = XUnitReporter.createProperty("polarion-set-testrun-finished",
                config.testRunProps.get("set-testrun-finished"));
        properties.add(testRunFinished);

        Property dryRun = XUnitReporter.createProperty("polarion-dry-run", config.testRunProps.get("dry-run"));
        properties.add(dryRun);

        Property includeSkipped = XUnitReporter.createProperty("polarion-include-skipped",
                config.testRunProps.get("include-skipped"));
        properties.add(includeSkipped);

        Configurator cfg = XUnitReporter.createConditionalProperty("polarion-response", responseName, properties);
        cfg.set();
        cfg = XUnitReporter.createConditionalProperty("polarion-custom", null, properties);
        cfg.set();
        cfg = XUnitReporter.createConditionalProperty("polarion-testrun-title", null, properties);
        cfg.set();
        cfg = XUnitReporter.createConditionalProperty("polarion-testrun-id", null, properties);
        cfg.set();
        cfg = XUnitReporter.createConditionalProperty("polarion-template-id", null, properties);
        cfg.set();

        tsuites.setProperties(props);
        return tsuites;
    }

    /**
     * Simple setter for a Property
     *
     * TODO: replace this with a lambda
     *
     * @param name key
     * @param value value of the key
     * @return Property with the given name and value
     */
    private static Property createProperty(String name, String value) {
        Property prop = new Property();
        prop.setName(name);
        prop.setValue(value);
        return prop;
    }

    @FunctionalInterface
    interface Configurator {
        void set();
    }

    /**
     * Creates a Configurator functional interface useful to set properties for the XUnit importer
     *
     * @param name element name
     * @param value value for the element (might be attribute depending on XML element)
     * @param properties list of Property
     * @return The Configurator that can be used to set the given name and value
     */
    private static Configurator createConditionalProperty(String name, String value, List<Property> properties) {
        Configurator cfg;
        Property prop = new Property();
        prop.setName(name);

        switch(name) {
            case "polarion-template-id":
                cfg = () -> {
                    if (config.xunit.getTemplateId().getId().equals(""))
                        return;
                    prop.setValue(config.xunit.getTemplateId().getId());
                    properties.add(prop);
                };
                break;
            case "polarion-testrun-title":
                cfg = () -> {
                    if (config.xunit.getTestrun().getTitle().equals(""))
                        return;
                    prop.setValue(config.xunit.getTestrun().getTitle());
                    properties.add(prop);
                };
                break;
            case "polarion-testrun-id":
                cfg = () -> {
                    if (config.xunit.getTestrun().getId().equals(""))
                        return;
                    prop.setValue(config.xunit.getTestrun().getId());
                    properties.add(prop);
                };
                break;
            case "polarion-response":
                cfg = () -> {
                    if (config.xunit.getSelector().getVal().equals(""))
                        return;
                    prop.setName("polarion-response-" + value);
                    prop.setValue(config.xunit.getSelector().getVal());
                    properties.add(prop);
                };
                break;
            case "polarion-custom":
                cfg = () -> {
                    Map<String, String> customFields = config.customFields;
                    if (customFields.isEmpty())
                        return;
                    customFields.entrySet().forEach(entry -> {
                        String key = "polarion-custom-" + entry.getKey();
                        String val = entry.getValue();
                        if (!val.equals("")) {
                            Property p = new Property();
                            p.setName(key);
                            p.setValue(val);
                            properties.add(p);
                        }
                    });
                };
                break;
            default:
                cfg = null;
        }
        return cfg;
    }

    /**
     * Given the class and method name, find the xml description file
     *
     * @param className class part of method name
     * @param methName only the method name
     * @return File which is path to the fully qualified method
     */
    private static File getXMLDescFile(String className, String methName) {
        String tcXMLPath = cfg.getTestcasesXml().getPath();
        String projID = cfg.getProject();

        Path path = Paths.get(tcXMLPath, projID, className, methName + ".xml");
        File xmlDesc = path.toFile();
        if(!xmlDesc.exists()) {
            XUnitReporter.logger.error("Could not find xml description file for " + path.toString());
            throw new XMLDescriptionError();
        }

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
            if (tcase.getId() == null)
                return "";
            return tcase.getId();
        }
        else if (t == Requirement.class) {
            XUnitReporter.logger.error("Not using @Requirement until requirement importer is done");
            throw new RequirementAnnotationException();
        }
        else
            throw new XMLDescriptionError();
    }

    public static com.github.redhatqe.polarize.importer.testcase.Testcase
    setPolarionIDFromXML(File xmlDesc, String id) {
        Optional<com.github.redhatqe.polarize.importer.testcase.Testcase> tc;
        tc = XUnitReporter.getTestcaseFromXML(xmlDesc);
        if (!tc.isPresent())
            throw new XMLUnmarshallError();
        com.github.redhatqe.polarize.importer.testcase.Testcase tcase = tc.get();
        if (tcase.getId() != null && !tcase.getId().equals(""))
            XUnitReporter.logger.warn("ID already exists...overwriting");
        tcase.setId(id);
        return tcase;
    }

    private static Optional<com.github.redhatqe.polarize.importer.testcase.Testcase> getTestcaseFromXML(File xmlDesc) {
        JAXBReporter jaxb = new JAXBReporter();
        Optional<com.github.redhatqe.polarize.importer.testcase.Testcase> tc;
        tc = IJAXBHelper.unmarshaller(com.github.redhatqe.polarize.importer.testcase.Testcase.class, xmlDesc,
                jaxb.getXSDFromResource(com.github.redhatqe.polarize.importer.testcase.Testcase.class));
        if (!tc.isPresent())
            return Optional.empty();
        com.github.redhatqe.polarize.importer.testcase.Testcase tcase = tc.get();
        return Optional.of(tcase);
    }

    /**
     * Program to make an XUnit import request
     *
     * @param args url, user, pw, path to xml, selector
     */
    public static void main(String[] args) throws InterruptedException, ExecutionException, JMSException {
        if (args.length != 5) {
            XUnitReporter.logger.error("url, user, pw, path to xml");
            return;
        }
        File xml = new File(args[3]);
        ImporterRequest.sendImportRequest(args[0], args[1], args[2], xml, args[4], XUnitReporter.xunitMessageHandler());
    }
}
