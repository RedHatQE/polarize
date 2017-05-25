package com.github.redhatqe.polarize.junitreporter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.github.redhatqe.polarize.FileHelper;
import com.github.redhatqe.polarize.IJAXBHelper;
import com.github.redhatqe.polarize.IdParams;
import com.github.redhatqe.polarize.JAXBHelper;
import com.github.redhatqe.polarize.configuration.ConfigType;
import com.github.redhatqe.polarize.configuration.XMLConfig;
import com.github.redhatqe.polarize.exceptions.*;
import com.github.redhatqe.polarize.importer.ImporterRequest;
import com.github.redhatqe.polarize.importer.xunit.*;
import com.github.redhatqe.polarize.importer.xunit.Error;
import com.github.redhatqe.polarize.metadata.Requirement;
import com.github.redhatqe.polarize.metadata.TestDefinition;
import com.github.redhatqe.polarize.utils.Tuple;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.*;
import org.testng.xml.XmlClass;
import org.testng.xml.XmlSuite;
import org.testng.xml.XmlTest;

import javax.jms.JMSException;

import java.io.*;
import java.nio.file.Files;
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
 * configured through the polarize-config.xml file.  A default configuration is contained in the resources folder, but
 * a global environment variable of XUNIT_IMPORTER_CONFIG can also be set.  If this env var exists and it points to a
 * file, this file will be loaded instead.
 */
public class XUnitReporter implements IReporter {
    private final static Logger logger = LoggerFactory.getLogger(XUnitReporter.class);
    public static String configPath = System.getProperty("polarize.config");
    public static File cfgFile = null;
    static {
        if (configPath != null)
            cfgFile = new File(configPath);
    }
    private static XMLConfig config = new XMLConfig(cfgFile);
    private final static ConfigType cfg = config.config;
    private final static File defaultPropertyFile =
            new File(System.getProperty("user.home") + "/.polarize/reporter.properties");
    private static List<String> failedSuites = new ArrayList<>();

    public final static String templateId = "polarion-testrun-template-id";
    public final static String testrunId = "polarion-testrun-id";
    public final static String testrunTitle = "polarion-testrun-title";
    public final static String polarionCustom = "polarion-custom";
    public final static String polarionResponse = "polarion-response";
    private File bad = new File("/tmp/bad-tests.txt");

    public static void setXMLConfig(String path) {
        if (path == null || path.equals(""))
            return;
        File cfgFile = new File(path);
        XUnitReporter.config = new XMLConfig(cfgFile);
        logger.info("Set XUnitReporter config to " + path);
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

    public void setTestSuiteResults(Testsuite ts, FullResult fr, ITestContext ctx) {
        if (fr == null)
            return;

        if (fr.total != fr.fails + fr.errors + fr.skips + fr.passes) {
            String e = "Total number of tests run != fails + errors + skips + passes\n";
            String v = "                       %d !=    %d +     %d +    %d +     %d\n";
            v = String.format(v, fr.total, fr.fails, fr.errors, fr.skips, fr.passes);
            System.err.println(e + v);
        }
        int numErrors = fr.errorsByMethod.size();


        // The iterations feature of Polarion means that we don't need to specify how many times a permutation of a
        // method + args passed/failed/skipped etc.  That's why we don't directly use the fr numbers.
        int numFails = ctx.getFailedTests().size();
        int numSkips = ctx.getSkippedTests().size();
        int numTotal = ctx.getAllTestMethods().length;
        if (numErrors > 0)
            numFails = numFails - numErrors;
        if (numFails <= 0)
            numFails = 0;
        ts.setErrors(Integer.toString(numErrors));
        ts.setFailures(Integer.toString(numFails));
        ts.setSkipped(Integer.toString(numSkips));
        ts.setTests(Integer.toString(numTotal));
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
        Testsuites tsuites = XUnitReporter.initTestSuiteInfo(config.xunit.getSelector().getName());
        List<Testsuite> tsuite = tsuites.getTestsuite();

        if (this.bad.exists()) {
            try {
                Files.delete(this.bad.toPath());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // Get information for each <testsuite>
        suites.forEach(suite -> {
            // suite here for the rhsm-qe tests should only be one occurrence
            Map<String, ISuiteResult> results = suite.getResults();
            Map<String, Tuple<FullResult, List<Testcase>>> full = XUnitReporter.getMethodInfo(suite, this.bad);
            List<Testsuite> collected = results.entrySet().stream()
                    .map(es -> {
                        // the results that we iterate through is each <test> element from the suite.xml.  From our
                        // perspective each <testsuite> is effectively the <test>, and in turn we model each <test>
                        // as a Class in java
                        Testsuite ts = new Testsuite();
                        List<Testcase> tests = ts.getTestcase();
                        if (tests == null)
                            tests = new ArrayList<>();

                        String key = es.getKey();
                        ISuiteResult result = es.getValue();
                        ITestContext ctx = result.getTestContext();
                        XmlTest xt = ctx.getCurrentXmlTest();
                        List<XmlClass> clses = xt.getClasses();
                        Set<String> clsSet = clses.stream()
                                .map(x -> {
                                    String sptCls = x.getSupportClass().toString();
                                    sptCls = sptCls.replace("class ", "");
                                    return sptCls;
                                })
                                .collect(Collectors.toSet());

                        ts.setName(key);
                        Date start = ctx.getStartDate();
                        Date end = ctx.getEndDate();
                        double duration = (end.getTime() - start.getTime()) / 1000.0;
                        ts.setTime(Double.toString(duration));

                        // While I suppose it's possible, we should have only one or zero possible results from the map
                        // so findFirst should return at most 1.  When will we have zero?
                        List<Tuple<FullResult, List<Testcase>>> frList = full.entrySet().stream()
                                .filter(e -> {
                                    String cls = e.getKey();
                                    return clsSet.contains(cls);
                                })
                                .map(Map.Entry::getValue)
                                .collect(Collectors.toList());
                        Optional<Tuple<FullResult, List<Testcase>>> maybeFR = frList.stream().findFirst();
                        Tuple<FullResult, List<Testcase>> tup = maybeFR.orElse(new Tuple<>());
                        FullResult fr = tup.first;
                        List<Testcase> tcs = tup.second;
                        if (tcs != null)
                            tests.addAll(tcs);

                        setTestSuiteResults(ts, fr, ctx);
                        if (fr == null) {
                            //System.out.println(String.format("Skipping test for %s", ctx.toString()));
                            return null;  // No FullResult due to empty frList.  Will be filtered out
                        }
                        else
                            return ts;
                    })
                    .filter(Objects::nonNull)  // filter out any suites without FullResult
                    .collect(Collectors.toList());

            tsuite.addAll(collected);
        });

        // Now that we've gone through the suites, let's marshall this into an XML file for the XUnit Importer
        FullResult suiteResults = getSuiteResults(tsuites);
        System.out.println(String.format("Error: %d, Failures: %d, Success: %d, Skips: %d", suiteResults.errors,
                suiteResults.fails, suiteResults.passes, suiteResults.skips));
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
            }
            else {
                JsonNode n = node.get();
                if (n.size() == 0)
                    return;
                JsonNode root = n.get("root");
                try {
                    Boolean passed = root.get("status").textValue().equals("passed");
                    if (passed) {
                        logger.info("XUnit importer was successful");
                        logger.info(root.get("testrun-url").textValue());
                    }
                    else {
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
                } catch (NullPointerException npe) {
                    logger.error("Unknown format of message from bus");
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
    private static void getStatus(ITestResult result, Testcase tc, FullResult fr, String qual) {
        Throwable t = result.getThrowable();
        int status = result.getStatus();
        StringBuilder sb = new StringBuilder();
        fr.total++;
        switch(status) {
            // Unfortunately, TestNG doesn't distinguish between an assertion failure and an error.  The way to check
            // is if getThrowable() returns non-null
            case ITestResult.FAILURE:
                if (t != null && !(t instanceof java.lang.AssertionError)) {
                    fr.errors++;
                    if (!fr.errorsByMethod.contains(qual))
                        fr.errorsByMethod.add(qual);
                    Error err = new Error();
                    String maybe = t.getMessage();
                    if (maybe != null) {
                        String msg = t.getMessage().length() > 128 ? t.getMessage().substring(128) : t.getMessage();
                        err.setMessage(msg);
                    }
                    else
                        err.setMessage("java.lang.NullPointerException");
                    Arrays.stream(t.getStackTrace()).forEach(st -> sb.append(st.toString()).append("\n"));
                    err.setContent(sb.toString());
                    tc.getError().add(err);
                }
                else {
                    fr.fails++;
                    Failure fail = new Failure();
                    if (t != null)
                        fail.setContent(t.getMessage());
                    tc.getFailure().add(fail);
                }
                break;
            case ITestResult.SKIP:
                fr.skips++;
                tc.setSkipped("true");
                break;
            case ITestResult.SUCCESS:
                fr.passes++;
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

    public static boolean
    checkMethInMapping(Map<String, IdParams> inner, String qual, String project, File badMethods) {
        boolean in = true;
        if (inner == null || !inner.containsKey(project)) {
            String err = String.format("%s does not exist in mapping file for Project %s \n", qual, project);
            logger.error(err);
            try {
                FileWriter badf = new FileWriter(badMethods, true);
                BufferedWriter bw = new BufferedWriter(badf);
                bw.write(err);
                bw.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            in = false;
        }
        return in;
    }

    public FullResult getSuiteResults(Testsuites suites) {
        List<Testsuite> sList = suites.getTestsuite();
        return sList.stream()
                .reduce(new FullResult(),
                        (acc, s) -> {
                            acc.skips += Integer.parseInt(s.getSkipped());
                            acc.errors += Integer.parseInt(s.getErrors());
                            acc.fails += Integer.parseInt(s.getFailures());
                            acc.total += Integer.parseInt(s.getTests());
                            acc.passes = acc.passes + (acc.total - (acc.skips + acc.errors + acc.fails));
                            return acc;
                        },
                        FullResult::add);
    }

    /**
     * Gets information from each invoked method in the test suite
     *
     * @param suite suite that was run by TestNG
     * @return map of classname to a tuple of the FullResult and TestCase
     */
    private static Map<String, Tuple<FullResult, List<Testcase>>>
    getMethodInfo(ISuite suite, File badMethods) {
        List<IInvokedMethod> invoked = suite.getAllInvokedMethods();
        Map<String, Tuple<FullResult, List<Testcase>>> full = new HashMap<>();
        for(IInvokedMethod meth: invoked) {
            ITestNGMethod fn = meth.getTestMethod();
            if (!fn.isTest()) {
                //XUnitReporter.logger.info(String.format("Skipping non-test method %s", fn.getMethodName()));
                continue;
            }

            ITestClass clz = fn.getTestClass();
            String methname = fn.getMethodName();
            String classname = clz.getName();

            // Load the mapping file
            String project = XUnitReporter.cfg.getProject();
            String path = XUnitReporter.config.getMappingPath();
            File fpath = new File(path);
            if (!fpath.exists()) {
                String err = String.format("Could not find mapping file %s", path);
                XUnitReporter.logger.error(err);
                throw new MappingError(err);
            }
            String qual = String.format("%s.%s", classname, methname);
            Map<String, Map<String, IdParams>> mapping = FileHelper.loadMapping(fpath);
            Map<String, IdParams> inner = mapping.get(qual);

            if (!checkMethInMapping(inner, qual, project, badMethods))
                continue;

            FullResult fres;
            Testcase testcase = new Testcase();
            List<Testcase> tests;
            if (!full.containsKey(classname)) {
                fres = new FullResult();
                tests = new ArrayList<>();
                tests.add(testcase);
                full.put(classname, new Tuple<>(fres, tests));
            }
            else {
                Tuple<FullResult, List<Testcase>> tup = full.get(classname);
                fres = tup.first;
                tests = tup.second;
                tests.add(testcase);
            }
            ITestResult result = meth.getTestResult();
            Double millis = (result.getEndMillis() - result.getStartMillis()) / 1000.0;

            fres.classname = classname;
            testcase.setTime(millis.toString());
            testcase.setName(methname);
            testcase.setClassname(classname);
            XUnitReporter.getStatus(result, testcase, fres, qual);

            // Create the <properties> element, and all the child <property> sub-elements from the iteration data.
            // Gets the IdParams from the mapping.json file which has all the parameter information
            IdParams ip = inner.get(project);
            String id = ip.getId();
            List<String> args = ip.getParameters();
            Property polarionID = XUnitReporter.createProperty("polarion-testcase-id", id);
            com.github.redhatqe.polarize.importer.xunit.Properties props =
                    getPropertiesFromMethod(result, args, polarionID);
            testcase.setProperties(props);
        }
        return full;
    }

    /**
     * Takes the parameter info from the mapping.json file for the TestCase ID, and generates the Properties for it
     *
     * @param result
     * @param args The list of args obtained from mapping.json for the matching polarionID
     * @param polarionID The matching Property of a Polarion ID for a TestCase
     * @return
     */
    public static com.github.redhatqe.polarize.importer.xunit.Properties
    getPropertiesFromMethod(ITestResult result, List<String> args, Property polarionID) {
        com.github.redhatqe.polarize.importer.xunit.Properties props =
                new com.github.redhatqe.polarize.importer.xunit.Properties();
        List<Property> tcProps = props.getProperty();
        tcProps.add(polarionID);

        // Get all the iteration data
        Object[] params = result.getParameters();
        if (args.size() != params.length) {
            String name = String.format("testname: %s, methodname: %s", result.getTestName(), result.getMethod().getMethodName());
            XUnitReporter.logger.error(String.format("Length of parameters from %s not the same as from mapping file", name));
            String argList = args.stream().reduce("", (acc, n) -> acc + n + ",");
            logger.error(String.format("While checking args = %s", argList));
            throw new MappingError();
        }
        for(int x = 0; x < params.length; x++) {
            Property param = new Property();
            param.setName("polarion-parameter-" + args.get(x));
            String p;
            if (params[x] == null)
                p = "null";
            else
                p = params[x].toString();
            param.setValue(p);
            tcProps.add(param);
        }
        return props;
    }

    /**
     * Gets information from polarize-config to set as the elements in the <testsuites>
     *
     * @param responseName
     * @return
     */
    private static Testsuites initTestSuiteInfo(String responseName) {
        Testsuites tsuites = new Testsuites();
        com.github.redhatqe.polarize.importer.xunit.Properties props =
                new com.github.redhatqe.polarize.importer.xunit.Properties();
        List<Property> properties = props.getProperty();

        Property user = XUnitReporter.createProperty("polarion-user-id", cfg.getUser().getName());
        properties.add(user);

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

        Configurator cfg = XUnitReporter.createConditionalProperty(XUnitReporter.polarionResponse, responseName,
                                                                   properties);
        cfg.set();
        cfg = XUnitReporter.createConditionalProperty(XUnitReporter.polarionCustom, null, properties);
        cfg.set();
        cfg = XUnitReporter.createConditionalProperty(XUnitReporter.testrunTitle, null, properties);
        cfg.set();
        cfg = XUnitReporter.createConditionalProperty(XUnitReporter.testrunId, null, properties);
        cfg.set();
        cfg = XUnitReporter.createConditionalProperty(XUnitReporter.templateId, null, properties);
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
            case XUnitReporter.templateId:
                cfg = () -> {
                    if (config.xunit.getTemplateId().getId().equals(""))
                        return;
                    prop.setValue(config.xunit.getTemplateId().getId());
                    properties.add(prop);
                };
                break;
            case XUnitReporter.testrunTitle:
                cfg = () -> {
                    if (config.xunit.getTestrun().getTitle().equals(""))
                        return;
                    prop.setValue(config.xunit.getTestrun().getTitle());
                    properties.add(prop);
                };
                break;
            case XUnitReporter.testrunId:
                cfg = () -> {
                    if (config.xunit.getTestrun().getId().equals(""))
                        return;
                    prop.setValue(config.xunit.getTestrun().getId());
                    properties.add(prop);
                };
                break;
            case XUnitReporter.polarionResponse:
                cfg = () -> {
                    if (config.xunit.getSelector().getVal().equals(""))
                        return;
                    prop.setName(XUnitReporter.polarionResponse + "-" + value);
                    prop.setValue(config.xunit.getSelector().getVal());
                    properties.add(prop);
                };
                break;
            case XUnitReporter.polarionCustom:
                cfg = () -> {
                    Map<String, String> customFields = config.customFields;
                    if (customFields.isEmpty())
                        return;
                    customFields.entrySet().forEach(entry -> {
                        String key = XUnitReporter.polarionCustom + "-" + entry.getKey();
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
        String tcXMLPath = config.getTestcasesXMLPath();
        String projID = cfg.getProject();

        Path path = Paths.get(tcXMLPath, projID, className, methName + ".xml");
        File xmlDesc = path.toFile();
        if(!xmlDesc.exists()) {
            throw new XMLDescriptionError("Could not find xml description file for " + path.toString());
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
            throw new XMLDescriptionError(String.format("Unknown class %s to unmarshall to", t.toString()));
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
        OptionParser parser = new OptionParser();

        String defaultUrl = XUnitReporter.config.polarion.getUrl();
        defaultUrl += XUnitReporter.config.xunit.getEndpoint().getRoute();
        String defaultSelector = "%s='%s'";
        defaultSelector = String.format(defaultSelector, XUnitReporter.config.xunit.getSelector().getName(),
                XUnitReporter.config.xunit.getSelector().getVal());

        OptionSpec<String> urlOpt = parser.accepts("url").withRequiredArg().ofType(String.class)
                .defaultsTo(defaultUrl);
        OptionSpec<String> userOpt = parser.accepts("user").withRequiredArg().ofType(String.class)
                .defaultsTo(XUnitReporter.config.polarion.getUser());
        OptionSpec<String> pwOpt = parser.accepts("pass").withRequiredArg().ofType(String.class)
                .defaultsTo(XUnitReporter.config.polarion.getPassword());
        OptionSpec<String> xunitOpt = parser.accepts("xunit").withRequiredArg().ofType(String.class).required();
        OptionSpec<String> selectorOpt = parser.accepts("selector").withRequiredArg().ofType(String.class)
                .defaultsTo(defaultSelector);

        OptionSet opts = parser.parse(args);
        String xunit = opts.valueOf(xunitOpt);
        File xml = new File(xunit);
        String url = opts.valueOf(urlOpt);
        String pw = opts.valueOf(pwOpt);
        String user = opts.valueOf(userOpt);
        String selector = opts.valueOf(selectorOpt);

        if (!selector.contains("'")) {
            String[] tokens = selector.split("=");
            if (tokens.length != 2)
                throw new InvalidArgument("--selector must be in form of name=val");
            String name = tokens[0];
            String val = tokens[1];
            selector = String.format("%s='%s'", name, val);
            logger.info("Modified selector to " + selector);
        }

        if (xunit.startsWith("http")) {
            Optional<File> body = ImporterRequest.get(xunit, user, pw, "/tmp/testng-polarion.xml");
            if (body.isPresent())
                xml = body.get();
            else
                throw new ImportRequestError(String.format("Could not download %s", xml.toString()));
        }

        Optional<ObjectNode> node =
                ImporterRequest.sendImportRequest(url, user, pw, xml, selector, XUnitReporter.xunitMessageHandler(),
                configPath);
        ObjectNode n = node.orElseThrow(() -> new MessageError("Did not get a response message from CI Bus"));
    }
}
