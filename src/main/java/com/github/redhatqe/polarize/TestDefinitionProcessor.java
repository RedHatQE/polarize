package com.github.redhatqe.polarize;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.redhatqe.polarize.configuration.*;
import com.github.redhatqe.polarize.exceptions.*;
import com.github.redhatqe.polarize.importer.ImporterRequest;
import com.github.redhatqe.polarize.importer.testcase.*;
import com.github.redhatqe.polarize.junitreporter.XUnitReporter;
import com.github.redhatqe.polarize.metadata.*;

import com.github.redhatqe.polarize.importer.testcase.Testcase;
import com.github.redhatqe.polarize.utils.Consumer2;
import com.github.redhatqe.polarize.utils.Transformer;
import org.testng.annotations.Test;


import javax.annotation.processing.*;
import javax.jms.JMSException;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;
import java.io.*;
import java.lang.annotation.Annotation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.github.redhatqe.polarize.metadata.DefTypes.*;
import static com.github.redhatqe.polarize.metadata.DefTypes.Custom.*;


/**
 * This is the Annotation processor that will look for {@link TestDefinition} and {@link Requirement} annotations
 * <p/>
 * While compiling code, it will find methods (or classes for @Requirement) that are annotated and generate an XML
 * description which is suitable to be consumed by the WorkItem Importer.  The polarize.properties is used to set where
 * the generated XML files will go and be looked for.
 */
public class TestDefinitionProcessor extends AbstractProcessor {
    private Elements elements;
    private Messager msgr;
    private static Logger logger = LoggerFactory.getLogger(TestDefinitionProcessor.class);
    private String tcPath;
    private Map<String, Meta<Requirement>> methToRequirement;
    // map of qualified name -> {projectID: meta}
    private Map<String, Map<String,
                            Meta<Requirement>>> methToProjectReq;
    private Map<String, Map<String,
                            Meta<TestDefinition>>> methToProjectDef;
    private Map<String, String> methNameToTestNGDescription;
    private Map<Testcase, Meta<TestDefinition>> testCaseToMeta;
    // Map of qualified name -> { projectID: testcaseID }
    private Map<String, Map<String, IdParams>> mappingFile = new LinkedHashMap<>();
    public static JAXBHelper jaxb = new JAXBHelper();
    private Testcases testcases = new Testcases();
    private Map<String, List<Testcase>> tcMap = new HashMap<>();
    private XMLConfig config;
    private ConfigType cfg;
    public List<Meta<TestDefinition>> badAnnotations = new ArrayList<>();
    public static Map<String, WarningInfo> warnings = new HashMap<>();

    /**
     * Recursive function that will get the fully qualified name of a method.
     *
     * @param elem Element object to recursively ascend up
     * @param accum a string which accumulate the qualified name
     * @param m Meta object whose values will be initialized as the function recurses
     * @return the fully qualified path of the starting Element
     */
    private String getTopLevel(Element elem, String accum, Meta m) {
        String tmp = elem.getSimpleName().toString();
        switch(elem.getKind()) {
            case PACKAGE:
                m.packName = elements.getPackageOf(elem).toString();
                tmp = m.packName;
                break;
            case CLASS:
                m.className = tmp;
                break;
            case METHOD:
                m.methName = tmp;
                break;
        }

        if (!Objects.equals(accum, "")) {
            accum = tmp + "." + accum;
        }
        else {
            accum = tmp;
        }

        if (elem.getKind() == ElementKind.PACKAGE)
            return accum;
        else {
            Element enclosing = elem.getEnclosingElement();
            return this.getTopLevel(enclosing, accum, m);
        }
    }

    private <T> Meta<T> getTopLevel(Element elem, Meta<T> m) {
        if (m == null)
            m = new Meta<>();
        if (m.qualifiedName == null)
            m.qualifiedName = "";
        m.qualifiedName = this.getTopLevel(elem, m.qualifiedName, m);
        return m;
    }


    /**
     * Creates a list of Meta objects from a list of Element objects
     *
     * @param elements list of Elements
     * @param ann an Annotation class (eg TestDefinition.class)
     * @param <T> type that is of an Annotation
     * @return a list of Metas of type T
     */
    private <T extends Annotation> List<Meta<T>> makeMeta(List<? extends Element> elements, Class<T> ann){
        List<Meta<T>> metas = new ArrayList<>();
        for(Element e : elements) {
            Meta<T> m = new Meta<>();
            m.qualifiedName = this.getTopLevel(e, "", m);
            m.annotation = e.getAnnotation(ann);
            metas.add(m);
        }
        return metas;
    }


    private List<Parameter> findParameterData(Element e) {
        List<Parameter> parameters = new ArrayList<>();
        if (e instanceof ExecutableElement) {
            ExecutableElement exe = (ExecutableElement) e;
            List<? extends VariableElement> params = exe.getParameters();
            params.forEach(
                    p -> {
                        this.logger.info(p.getSimpleName().toString());
                        Parameter param = new Parameter();
                        param.setName(p.getSimpleName().toString());
                        param.setScope("local");
                        parameters.add(param);
                    }
            );
        }
        return parameters;
    }

    /**
     * Checks that the length of the projectID and testCaseID are equal
     *
     * If a user specifies 2 projectID, then they can not rely on the default behavior of testCaseID since the
     * default for testCaseID is a single {""}.  If 2 or more projectID are specified, there must be an equal
     * number specified in the testCaseID.  This also works conversely.  If there are multiple testCaseID, then
     * the same number of projectID must be specified.
     *
     * @param meta
     * @param name
     */
    public void validateProjectToTestCase(TestDefinition meta, String name) {
        int plength = meta.projectID().length;
        int tclength = meta.testCaseID().length;
        if (plength != tclength) {
            String err = "TestDefinition for %s: \n";
            String err2 = "projectID and testCaseID array size are not equal. Check your annotation\n";
            String err3 = "projectID = %s, testCaseID = %s";
            err = String.format(err, name);
            Project[] projects = meta.projectID();
            String pString = Arrays.stream(projects)
                    .reduce("",
                            (acc, i) -> acc + i + ",",
                            (acc, next) -> acc + next);
            pString = pString.substring(pString.length() -1);
            String tcString = Arrays.stream(meta.testCaseID())
                    .reduce("",
                            (acc, i) -> acc + i + ",",
                            (acc, next) -> acc + next);
            tcString = tcString.substring(tcString.length() -1);
            err3 = String.format(err3, pString, tcString);
            logger.error(err + err2 + err3);
        }
    }

    /**
     * Find all methods annotated with multiple @TestDefinition and return a List of matching Meta\<TestDefinition\>
     *
     * The Element object will provide access to the raw TestDefinition data.  This annotation contains all
     * we need to create the Meta object which has all the metadata we need.  This method will find annotations with
     * multiple @TestDefinition applied to the method.
     *
     * @param elements
     * @return
     */
    private List<Meta<TestDefinition>>
    makeMetaFromTestDefinitions(List<? extends Element> elements){
        List<Meta<TestDefinition>> metas = new ArrayList<>();
        for(Element e : elements) {
            TestDefinitions container = e.getAnnotation(TestDefinitions.class);
            List<Parameter> params = this.findParameterData(e);
            for(TestDefinition r: container.value()) {
                Meta<TestDefinition> throwAway = this.getTopLevel(e, null);
                this.validateProjectToTestCase(r, throwAway.qualifiedName);
                int i = 0;
                for(Project project: r.projectID()) {
                    Boolean badAnn = false;
                    String testID = "";
                    if (i < r.testCaseID().length) {
                        testID = r.testCaseID()[i];
                        i++;
                    }
                    else
                        badAnn = true;

                    Meta<TestDefinition> m;
                    m = this.createMeta(r, params, project, e, testID, badAnn);
                    metas.add(m);
                }
            }
        }
        return metas;
    }

    // FIXME: I think this should be removed in favor of the Meta functions
    private Meta<TestDefinition>
    createMeta(TestDefinition r, List<Parameter> params, Project project, Element e, String testID, Boolean badAnn) {
        Meta<TestDefinition> m = new Meta<>();
        m.polarionID = testID;
        String full = this.getTopLevel(e, "", m);
        logger.info(String.format("Fully qualified name is %s", full));
        m.qualifiedName = full;
        m.annotation = r;
        m.project = project.toString();
        if (m.params == null)
            m.params = params;
        else if (m.params.isEmpty())
            m.params.addAll(params);
        if (badAnn)
            m.dirty = true;
        return m;
    }

    private List<Meta<TestDefinition>>
    makeMetaFromTestDefinition(List<? extends Element> elements){
        List<Meta<TestDefinition>> metas = new ArrayList<>();
        for(Element e : elements) {
            List<Parameter> params = this.findParameterData(e);
            TestDefinition def = e.getAnnotation(TestDefinition.class);
            Meta<TestDefinition> throwAway = this.getTopLevel(e, null);
            this.validateProjectToTestCase(def, throwAway.qualifiedName);

            Boolean badAnn = false;
            int i = 0;
            for(Project project: def.projectID()) {
                String testID = "";
                if (i < def.testCaseID().length) {
                    testID = def.testCaseID()[i];
                    i++;
                }
                else
                    badAnn = true;

                Meta<TestDefinition> m;
                m = this.createMeta(def, params, project, e, testID, badAnn);
                metas.add(m);
                i++;
            }
        }
        return metas;
    }


    /**
     * The TestDefinitionProcessor actually needs to look for three annotation types:
     * <ul>
     *   <li>{@link TestDefinition}: to get TestDefinition WorkItem information</li>
     *   <li>{@link Requirement}: to get Requirement WorkItem information</li>
     *   <li>{@link Test}: to get the existing description</li>
     * </ul>
     *
     * @param set passed from compiler
     * @param roundEnvironment passed from compiler
     * @return true if processed successfully, false otherwise
     */
    @Override
    public boolean process(Set<? extends TypeElement> set, RoundEnvironment roundEnvironment) {
        System.out.println("In process() method");

        // load the mapping file
        File mapPath = new File(this.config.getMappingPath());
        System.out.println(mapPath.toString());
        if (mapPath.exists()) {
            System.out.println("Loading the map");
            this.mappingFile = FileHelper.loadMapping(mapPath);
            //System.out.println(this.mappingFile.toString());
        }

        /* ************************************************************************************
         * Get all the @TestDefinition annotations which were annotated on an element only once.
         * Make a list of Meta types that store the fully qualified name of every @TestDefinition annotated
         * method.  We will use this to create a map of qualified_name => TestDefinition Annotation
         **************************************************************************************/
        TreeSet<ElementKind> allowed = new TreeSet<>();
        allowed.add(ElementKind.METHOD);
        String err = "Can only annotate methods with @TestDefinition";
        List<? extends Element> polAnns = this.getAnnotations(roundEnvironment, TestDefinition.class, allowed, err);
        List<Meta<TestDefinition>> metas = this.makeMetaFromTestDefinition(polAnns);

        /* Get all the @TestDefinition annotations which have been repeated on a single element. */
        List<? extends Element> pols = this.getAnnotations(roundEnvironment, TestDefinitions.class, allowed, err);
        metas.addAll(this.makeMetaFromTestDefinitions(pols));

        this.methToProjectDef = this.createMethodToMetaPolarionMap(metas);

        /* Get all the @Test annotations in order to get the description */
        Map<String, String> methToDescription = this.getTestAnnotations(roundEnvironment);
        this.methNameToTestNGDescription.putAll(methToDescription);

        // FIXME: Put this back when the CI Ops team has a Requirement importer
        /*List<ReqType> reqList = this.processAllRequirements();
          if (reqList == null)
            return false;
         */
        List<Testcase> tests = this.processAllTC();

        /* testcases holds all the methods that need a new or updated Polarion TestCase */
        logger.info("Updating testcases that had no testcaseId...");
        this.tcImportRequest();
        TestDefinitionProcessor.updateMappingFile(this.mappingFile, this.methToProjectDef, this.tcPath);

        // Update the mapping file
        if (this.methToProjectDef.size() > 0) {
            this.createMappingFile(mapPath);
            try {
                Files.lines(Paths.get(mapPath.toString())).forEach(System.out::println);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        else {
            logger.info("Did not update the mapping file");
        }

        this.printWarnings(warnings);
        this.tcMap = new HashMap<>();
        this.mappingFile = new HashMap<>();
        return true;
    }

    private void printWarnings(Map<String, WarningInfo> warns) {
        if (this.tcMap.isEmpty()) {
            String warnMsg = warnings.entrySet().stream()
                    .map(es -> {
                        String meth = es.getKey();
                        WarningInfo wi = es.getValue();
                        String msg = String.format("In %s- %s : %s", meth, wi.project, wi.wt.message());
                        return msg;
                    })
                    .reduce("", (acc, n) -> String.format("%s%s\n", acc, n));
            logger.warn(warnMsg);
        }
    }

    private static void addBadFunction(String qualName, String project, List<String> badFuncs) {
        String err = "No ID in XML or annotation.  Check your Annotation %s in %s";
        err = String.format(err, qualName, project);
        logger.error(err);
        badFuncs.add(err);
    }

    private static void writeBadFunctionText(List<String> badFunctions) {
        if (badFunctions.size() > 0)
            logger.error("You must check your annotations or enable the TestCase importer in your config " +
                    "before using your project with the XUnit Importer.  Please see the file " +
                    "/tmp/bad-functions.txt for all the functions to check");
        try {
            Files.write(Paths.get("/tmp/bad-functions.txt"), badFunctions);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void updateMappingFile(Map<String, Map<String, IdParams>> mapFile,
                                         Map<String, Map<String, Meta<TestDefinition>>> methMap,
                                         String tcpath) {
        List<String> badFunctions = new ArrayList<>();
        methMap.entrySet().forEach(e -> {
            String fnName = e.getKey();
            Map<String, Meta<TestDefinition>> projectToMeta = e.getValue();
            projectToMeta.entrySet().forEach(es -> {
                String project = es.getKey();
                Meta<TestDefinition> meta = es.getValue();
                String id = meta.getPolarionIDFromTestcase()
                        .orElseGet(() -> meta.getPolarionIDFromXML(tcpath).orElse(""));
                Boolean badFn = false;
                // Check if the mapFile has the corresponding project of this function name
                if (!mapFile.containsKey(fnName) || !mapFile.get(fnName).containsKey(project)) {
                    if (id.equals("")) badFn = true;
                }
                else {
                    String newid = mapFile.get(fnName).get(project).getId();
                    if (newid.equals("")) badFn= true;
                    if (id.equals("")) id = newid;
                }

                if (badFn)
                    TestDefinitionProcessor.addBadFunction(meta.qualifiedName, project, badFunctions);
                TestDefinitionProcessor.addToMapFile(mapFile, meta, id);
            });
        });
        TestDefinitionProcessor.writeBadFunctionText(badFunctions);
    }

    /**
     * Runs processTC on all the entries in methToProjectDef
     *
     * @return List of all the processed TestCases
     */
    private List<Testcase> processAllTC() {
        return this.methToProjectDef.entrySet().stream()
                .flatMap(es -> es.getValue().entrySet().stream()
                        .map(val -> {
                            Meta<TestDefinition> meta = val.getValue();
                            return this.processTC(meta);
                        })
                        .collect(Collectors.toList()).stream())
                .collect(Collectors.toList());
    }

    /**
     * Generates an XML description file equivalent to the Polarion definition
     *
     * @param tc the Testcase object to be marshalled into XML
     * @param path path to where the XML file will go
     */
    private static void createTestCaseXML(Testcase tc, File path) {
        JAXBHelper jaxb = new JAXBHelper();
        logger.info(String.format("Generating XML description in %s", path.toString()));
        IFileHelper.makeDirs(path.toPath());
        IJAXBHelper.marshaller(tc, path, jaxb.getXSDFromResource(Testcase.class));
    }

    /**
     * Sets all the information necessary for the testcases object, and marshalls it to an XML file
     *
     * The XML that is created by this call can be used for the sendImportRequest method
     * @param selectorName name part of JMS selector string
     * @param selectorValue value part of JMS selector string
     * @param pID project ID
     * @param testXml File of where the XML representation will go
     * @return
     */
    private Optional<String> initTestcases(String selectorName, String selectorValue, String pID, File testXml) {
        String author = this.cfg.getAuthor();
        return TestDefinitionProcessor.initTestcases(selectorName, selectorValue, pID, author, testXml, this.tcMap,
                this.testcases);
    }

    /**
     * Initializes the Testcases object and returns an optional project ID
     *
     * @param selectorName name part of selector
     * @param selectorValue value part of selector (eg <name>='<value>')
     * @param projectID project ID of the Testcases object
     * @param author author for the Testcases
     * @param testcaseXml File to where the Testcases object will be marshalled to
     * @param testMap a map of methodName to Testcase list
     * @param tests the Testcases object that will be initialized
     * @return an optional of the Testcases project
     */
    private static Optional<String>
    initTestcases(String selectorName, String selectorValue, String projectID, String author, File testcaseXml,
                  Map<String, List<Testcase>> testMap, Testcases tests) {
        if (!testMap.containsKey(projectID)) {
            logger.error("ProjectType ID does not exist within Testcase Map");
            return Optional.empty();
        }
        if (testMap.get(projectID).isEmpty()) {
            logger.info(String.format("No testcases for %s to import", projectID));
            return Optional.empty();
        }
        tests.setProjectId(projectID);
        //tests.setUserId(author);
        tests.getTestcase().addAll(testMap.get(projectID));

        ResponseProperties respProp = tests.getResponseProperties();
        if (respProp == null)
            respProp = new ResponseProperties();
        tests.setResponseProperties(respProp);
        List<ResponseProperty> props = respProp.getResponseProperty();
        if (!props.stream().anyMatch(p -> p.getName().equals(selectorName) && p.getValue().equals(selectorValue))) {
            ResponseProperty rprop = new ResponseProperty();
            rprop.setName(selectorName);
            rprop.setValue(selectorValue);
            props.add(rprop);
        }

        JAXBHelper jaxb = new JAXBHelper();
        IJAXBHelper.marshaller(tests, testcaseXml, jaxb.getXSDFromResource(Testcases.class));
        return Optional.of(projectID);
    }

    /**
     * Sends an import request for each project
     *
     * @param testcaseMap
     * @param selectorName
     * @param selectorValue
     * @param author
     * @param url
     * @param user
     * @param pw
     * @param tests
     * @return
     */
    public static List<Optional<ObjectNode>>
    tcImportRequest(Map<String, List<Testcase>> testcaseMap, String selectorName, String selectorValue, String author,
                    String url, String user, String pw, Testcases tests) {
        List<Optional<ObjectNode>> maybeNodes = new ArrayList<>();
        if (testcaseMap.isEmpty())
            return maybeNodes;

        String selector = String.format("%s='%s'", selectorName, selectorValue);
        for(String project: testcaseMap.keySet()) {
            String path = String.format("/tmp/testcases-%s.xml", project);
            File testXml = new File(path);
            if (!TestDefinitionProcessor.initTestcases(selectorName, selectorValue, project, author, testXml,
                    testcaseMap, tests).isPresent())
                maybeNodes.add(Optional.empty());
            else {
                try {
                    Consumer<Optional<ObjectNode>> hdlr;
                    hdlr = TestDefinitionProcessor.testcaseImportHandler(path, project, tests);
                    maybeNodes.add(ImporterRequest.sendImportRequest(url, user, pw, testXml, selector, hdlr));
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (ExecutionException e) {
                    logger.warn("FIXME: Retry due to exception");
                    e.printStackTrace();
                } catch (JMSException e) {
                    logger.warn("TODO: Retry after a sleep");
                    e.printStackTrace();
                }
            }
        }
        return maybeNodes;
    }

    private List<Optional<ObjectNode>> tcImportRequest() {
        List<Optional<ObjectNode>> maybeNodes = new ArrayList<>();
        if (this.tcMap.isEmpty()) {
            logger.info("No more testcases to import ...");
            return maybeNodes;
        }

        for(String project: this.tcMap.keySet()) {
            String selectorName = this.config.testcase.getSelector().getName();
            String selectorValue = this.config.testcase.getSelector().getVal();
            String selector = String.format("%s='%s'", selectorName, selectorValue);
            String path = String.format("/tmp/testcases-%s.xml", project);
            File testXml = new File(path);
            if (!this.initTestcases(selectorName, selectorValue, project, testXml).isPresent())
                maybeNodes.add(Optional.empty());
            else {
                try {
                    maybeNodes.add(this.sendTCImporterRequest(selector, testXml));
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (ExecutionException e) {
                    logger.warn("FIXME: Retry on ExecutionException");
                    e.printStackTrace();
                } catch (JMSException e) {
                    logger.warn("TODO: Retry due to JMS Exception");
                    e.printStackTrace();
                }
            }
        }
        return maybeNodes;
    }

    private Consumer<Optional<ObjectNode>> testcaseImportHandler() {
        return TestDefinitionProcessor.testcaseImportHandler(this.tcPath, this.cfg.getProject(), this.testcases);
    }


    /**
     * Returns a lambda usable as a handler for ImporterRequest.sendImportRequest
     *
     * This handler will take the ObjectNode (for example, decoded from a message on the message bus) gets the Polarion
     * ID from the ObjectNode, and edits the XML file with the Id.  It will also store
     *
     * @param testPath path to where the testcase XML defs will be stored
     * @param pID project ID to be worked on
     * @param tcs Testcases object to be examined
     * @return lambda of a Consumer
     */
    public static Consumer<Optional<ObjectNode>> testcaseImportHandler(String testPath, String pID, Testcases tcs) {
        return node -> {
            if (!node.isPresent()) {
                logger.warn("No message was received");
                return;
            }
            JsonNode root = node.get().get("root");
            if (root.has("status")) {
                if (root.get("status").textValue().equals("failed"))
                    return;
            }
            JsonNode testcases = root.get("import-testcases");
            testcases.forEach(n -> {
                String name = n.get("name").textValue();
                if (!n.get("status").textValue().equals("failed")) {
                    String id = n.get("id").toString();
                    if (id.startsWith("\""))
                        id = id.substring(1);
                    if (id.endsWith("\""))
                        id = id.substring(0, id.length() -1);
                    logger.info("New Testcase id = " + id);
                    Optional<Path> maybeXML = FileHelper.getXmlPath(testPath, name, pID);
                    if (!maybeXML.isPresent()) {
                        // In this case, we couldn't get the XML path due to a bad name or tcPath
                        String err = String.format("Couldn't generate XML path for %s and %s", testPath, name);
                        logger.error(err);
                        throw new InvalidArgument();
                    }
                    else {
                        Path xmlDefinition = maybeXML.get();
                        File xmlFile = xmlDefinition.toFile();
                        if (!xmlFile.exists()) {
                            logger.info("No XML file exists...generating one");
                            Testcase matched = TestDefinitionProcessor.findTestcaseByName(name, tcs);

                            IJAXBHelper.marshaller(matched, xmlFile, jaxb.getXSDFromResource(Testcase.class));
                        }
                        logger.debug(String.format("Found %s for method %s", xmlDefinition.toString(), name));
                        logger.debug("Unmarshalling to edit the XML file");
                        Testcase tc = XUnitReporter.setPolarionIDFromXML(xmlDefinition.toFile(), id);
                        if (!tc.getId().equals(id)) {
                            logger.error("Setting the id for the XML on the Testcase failed");
                            throw new XMLEditError();
                        }
                        IJAXBHelper.marshaller(tc, xmlFile, jaxb.getXSDFromResource(Testcase.class));
                    }
                }
                else {
                    logger.error(String.format("Unable to create testcase for %s", name));
                }
            });
        };
    }

    /**
     * Finds a Testcase in testcases by matching name to the titles of the testcase
     *
     * @param name qualified name of method
     * @return the matching Testcase for the name
     */
    public static Testcase findTestcaseByName(String name, Testcases tests) {
        List<Testcase> tcs = tests.getTestcase().stream()
                .filter(tc -> {
                    String title = tc.getTitle();
                    return title.equals(name);
                })
                .collect(Collectors.toList());
        if (tcs.size() != 1) {
            logger.error("Found more than one matching qualified name in testcases");
            throw new SizeError();
        }
        return tcs.get(0);
    }

    private Optional<ObjectNode> sendTCImporterRequest(String selector, File testcaseXml)
            throws InterruptedException, ExecutionException, JMSException {
        if (!this.config.testcase.isEnabled())
            return Optional.empty();
        String url = this.config.polarion.getUrl() + this.config.testcase.getEndpoint().getRoute();
        String user = this.config.polarion.getUser();
        String pw = this.config.polarion.getPassword();
        return ImporterRequest.sendImportRequest(url, user, pw, testcaseXml, selector, this.testcaseImportHandler());
    }

    /**
     * Creates a map from qualified method name to {projectID: Meta object}
     *
     * @param metas a list of Metas to generate
     * @return map from method name to map of projectID to Meta object
     */
    private Map<String, Map<String, Meta<TestDefinition>>>
    createMethodToMetaPolarionMap(List<Meta<TestDefinition>> metas) {
        Map<String, Map<String, Meta<TestDefinition>>> methods = new HashMap<>();
        for(Meta<TestDefinition> m: metas) {
            String meth = m.qualifiedName;
            String project = m.project;

            if (!methods.containsKey(meth)) {
                Map<String, Meta<TestDefinition>> projects = new HashMap<>();
                projects.put(project, m);
                methods.put(meth, projects);
            }
            else {
                Map<String, Meta<TestDefinition>> projects = methods.get(meth);
                if (!projects.containsKey(project)) {
                    projects.put(project, m);
                }
                else
                    logger.warn(String.format("ProjectType %s already exists for %s", project, meth));
            }
        }
        return methods;
    }

    /**
     * Creates a nested map of qualifiedName -> {projectID: Requirement}
     *
     * TODO: Figure out how to do this with reduce
     *
     * @param metas A list of Metas of type Requirement
     * @return a nested map qualifiedName to {projectId: Requirement}
     */
    private Map<String, Map<String, Meta<Requirement>>>
    createMethodToMetaRequirementMap(List<Meta<Requirement>> metas) {
        Map<String, Map<String, Meta<Requirement>>> acc = new HashMap<>();
        Map<String, Meta<Requirement>> projectToMeta = new HashMap<>();
        for(Meta<Requirement> m: metas) {
            Requirement r = m.annotation;
            String key = m.qualifiedName;
            String project = r.project().toString();
            projectToMeta.put(project, m);
            if (!acc.containsKey(key))
                acc.put(key, projectToMeta);
        }
        return acc;
    }

    /**
     * Gets annotations from the type specified by c
     *
     * @param env The RoundEnvironment (which was created and given by the compiler in process())
     * @param c The class of the Annotation we want to get
     * @param allowed A set of the ElementKind types that we are allowed to annotate c with (eg METHOD or CLASS)
     * @param errMsg An error message if something fails
     * @return List of the Elements that had an annotation of type c from the allowed set
     */
    private List<? extends Element>
    getAnnotations(RoundEnvironment env, Class<? extends Annotation> c, Set<ElementKind> allowed, String errMsg) {
        String iface = c.getSimpleName();
        return env.getElementsAnnotatedWith(c)
                .stream()
                .map(ae -> {
                    if (!allowed.contains(ae.getKind())){
                        this.errorMsg(ae, errMsg, iface);
                        return null;
                    }
                    return ae;
                })
                .filter(ae -> ae != null)
                .collect(Collectors.toList());
    }


    /**
     * Finds methods annotated with @Test and gets the description field.  It returns a map of
     * fully qualified name -> description
     *
     * @param env RoundEnvironment passed by during compilation
     * @return map of fully qualified name of method, to description of the method
     */
    private Map<String, String> getTestAnnotations(RoundEnvironment env) {
        List<? extends Element> testAnns = env.getElementsAnnotatedWith(Test.class)
                .stream().collect(Collectors.toList());

        List<Meta<Test>> tests = this.makeMeta(testAnns, Test.class);

        Map<String, String> nameToDesc = new HashMap<>();
        for(Meta<Test> mt: tests) {
            String key = mt.qualifiedName;
            String val = mt.annotation.description();
            nameToDesc.put(key, val);
        }
        return nameToDesc;
    }

    /**
     * Creates the TestSteps for the Testcase given values in the meta object
     *
     * @param meta Meta object containing parameter information
     * @param tc the Testcase object that will get TestSteps information added
     */
    private static void initTestSteps(Meta<TestDefinition> meta, Testcase tc) {
        com.github.redhatqe.polarize.importer.testcase.TestSteps isteps = tc.getTestSteps();
        if (isteps == null) {
            isteps = new com.github.redhatqe.polarize.importer.testcase.TestSteps();
        }
        List<com.github.redhatqe.polarize.importer.testcase.TestStep> tsteps = isteps.getTestStep();

        // Takes a List<Parameter> and returns a TestStepColumn
        Transformer<List<Parameter>, TestStepColumn> parameterize = args -> {
            TestStepColumn col = new TestStepColumn();
            col.setId("step");
            args.forEach(a -> col.getContent().add(a));
            return col;
        };

        // For automation needs, we will only ever have one TestStep (but perhaps with multiple columns).
        com.github.redhatqe.polarize.importer.testcase.TestStep ts =
                new com.github.redhatqe.polarize.importer.testcase.TestStep();
        List<TestStepColumn> cols = ts.getTestStepColumn();
        if (meta.params != null && meta.params.size() > 0) {
            TestStepColumn tcolumns = parameterize.transform(meta.params);
            cols.add(tcolumns);
        }
        else {
            TestStepColumn tsc = new TestStepColumn();
            tsc.setId("step");
            cols.add(tsc);
        }
        tsteps.add(ts);
        tc.setTestSteps(isteps);
    }

    private static String getPolarionIDFromDef(TestDefinition def, String project) {
        int index = -1;
        Project[] projects = def.projectID();
        String[] ids = def.testCaseID();
        if (ids.length == 0)
            return "";

        for(int i = 0; i < projects.length; i++) {
            if (projects[i].toString().equals(project)) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            throw new PolarionMappingError("The meta.project value not found in TestDefintion.projectID()");
        }
        String pName;
        try {
            pName = ids[index];
        }
        catch (ArrayIndexOutOfBoundsException ex) {
            // This means that there were more elements in projectID than testCaseID.  Issue a warning, as this
            // could be a bug.  It can happen like this:
            // projectID={RHEL6, RedHatEnterpriseLinux7},
            // testCaseID="RHEL6-23478",
            pName = "";
        }
        return pName;
    }

    public static Optional<String>
    getPolarionIDFromMapFile(String name, String project, Map<String, Map<String, IdParams>> mapFile) {
        Map<String, IdParams> pToID = mapFile.getOrDefault(name, null);
        if (pToID == null)
            return Optional.empty();
        IdParams ip = pToID.getOrDefault(project, null);
        if (ip == null)
            return Optional.empty();
        String id = ip.id;
        if (id.equals(""))
            return Optional.empty();
        else
            return Optional.of(id);
    }

    public static void
    setPolarionIDInMapFile(String name, String project, String id, Map<String, Map<String, IdParams>> mapFile) {
        Map<String, IdParams> pToI;
        if (mapFile.containsKey(name)) {
            pToI = mapFile.get(name);
        }
        else {
            pToI =new LinkedHashMap<>();
        }

        IdParams ip = pToI.getOrDefault(project, null);
        if (ip != null) {
            ip.id = id;
        }
        else {
            ip = new IdParams(id, new LinkedList<>());
        }
        pToI.put(project, ip);
        mapFile.put(name, pToI);
    }

    /**
     * FIXME: I think this would have been better implemented as a composition
     *
     * Returns a lambda of a Consumer<DefType.Custom> that can be used to set custom fields
     *
     * @param supp Functional interface that takes a key, value args
     * @param def a TestDefinition object used to supply a value
     * @return
     */
    public static Consumer<DefTypes.Custom> customFieldsSetter(Consumer2<String, String> supp, TestDefinition def) {
        return key -> {
            switch (key) {
                case CASEAUTOMATION:
                    supp.accept(CASEAUTOMATION.stringify(), def.automation().stringify());
                    break;
                case CASEIMPORTANCE:
                    supp.accept(CASEIMPORTANCE.stringify(), def.importance().stringify());
                    break;
                case CASELEVEL:
                    supp.accept(CASELEVEL.stringify(), def.level().stringify());
                    break;
                case CASEPOSNEG:
                    supp.accept(CASEPOSNEG.stringify(), def.posneg().stringify());
                    break;
                case UPSTREAM:
                    supp.accept(UPSTREAM.stringify(), def.upstream());
                    break;
                case TAGS:
                    supp.accept(TAGS.stringify(), def.tags());
                    break;
                case SETUP:
                    supp.accept(SETUP.stringify(), def.setup());
                    break;
                case TEARDOWN:
                    supp.accept(TEARDOWN.stringify(), def.teardown());
                    break;
                case COMPONENT:
                    supp.accept(COMPONENT.stringify(), def.component());
                    break;
                case SUBCOMPONENT:
                    supp.accept(SUBCOMPONENT.stringify(), def.subcomponent());
                    break;
                case AUTOMATION_SCRIPT:
                    supp.accept(AUTOMATION_SCRIPT.stringify(), def.script());
                    break;
                case TESTTYPE:
                    supp.accept(TESTTYPE.stringify(), def.testtype().testtype().stringify());
                    break;
                case SUBTYPE1:
                    supp.accept(SUBTYPE1.stringify(), def.testtype().subtype1().toString());
                    break;
                case SUBTYPE2:
                    supp.accept(SUBTYPE2.stringify(), def.testtype().subtype2().toString());
                    break;
                default:
                    logger.warn(String.format("Unknown enum value: %s", key.toString()));
            }
        };
    }

    /**
     * Creates and initializes a Testcase object
     *
     * This function is mainly used to setup a Testcase object to be used for a Testcase importer request
     *
     * @param meta Meta object used to intialize Testcase information
     * @param methToDesc A map which looks up method name to description
     * @return Testcase object
     */
    public static Testcase initImporterTestcase(Meta<TestDefinition> meta, Map<String, String> methToDesc) {
        Testcase tc = new Testcase();
        TestDefinition def = meta.annotation;
        TestDefinitionProcessor.initTestSteps(meta, tc);
        CustomFields custom = tc.getCustomFields();
        if (custom == null)
            custom = new CustomFields();
        List<CustomField> fields = custom.getCustomField();
        DefTypes.Custom[] fieldKeys = {CASEAUTOMATION, CASEIMPORTANCE, CASELEVEL, CASEPOSNEG, UPSTREAM, TAGS, SETUP,
                                       TEARDOWN, AUTOMATION_SCRIPT, COMPONENT, SUBCOMPONENT, TESTTYPE, SUBTYPE1,
                                       SUBTYPE2};

        Consumer2<String, String> supp = (id, content) -> {
            CustomField field = new CustomField();
            if (!content.equals("")) {
                field.setId(id);
                field.setContent(content);
                fields.add(field);
            }
        };

        Consumer<DefTypes.Custom> transformer = TestDefinitionProcessor.customFieldsSetter(supp, def);
        for(DefTypes.Custom cust: fieldKeys) {
            transformer.accept(cust);
        }

        if (def.description().equals("") && methToDesc != null)
            tc.setDescription(methToDesc.get(meta.qualifiedName));
        else
            tc.setDescription(def.description());

        if (def.title().equals(""))
            tc.setTitle(meta.qualifiedName);
        else
            tc.setTitle(def.title());

        TestDefinitionProcessor.setLinkedWorkItems(tc, def);
        tc.setId(TestDefinitionProcessor.getPolarionIDFromDef(def, meta.project));
        tc.setCustomFields(custom);
        return tc;
    }

    private static void setLinkedWorkItems(Testcase tc, TestDefinition ann) {
        LinkedItem[] li = ann.linkedWorkItems();
        LinkedWorkItems lwi = tc.getLinkedWorkItems();
        if (lwi == null)
            lwi = new LinkedWorkItems();
        List<LinkedWorkItem> links = lwi.getLinkedWorkItem();
        links.addAll(Arrays.stream(li)
                .map(wi -> {
                    LinkedWorkItem tcLwi = new LinkedWorkItem();
                    tcLwi.setWorkitemId(wi.workitemId());
                    tcLwi.setRoleId(wi.role().toString());
                    return tcLwi;
                })
                .collect(Collectors.toList()));
        if (links.size() > 0)
            tc.setLinkedWorkItems(lwi);
    }

    /**
     * Given information from a Meta<TestDefinition> object and a Polarion ID for a TestCase, add to the mapFile
     *
     * Since the Meta object may not contain the polarionID, it is necessary to pass a non-null and valid ID.
     *
     * @param mapFile a map of function name to map of project -> parameter info
     * @param meta Meta of type TestDefinition used to get information for mapFile
     * @param id the string of the Polarion ID for t
     */
    public static void addToMapFile(Map<String, Map<String, IdParams>> mapFile, Meta<TestDefinition> meta, String id) {
        // TODO: The ID exists in the XML file, but not in the annotation.  Set the mapping file with this info
        Map<String, IdParams> projToId = mapFile.getOrDefault(meta.qualifiedName, null);
        if (projToId != null) {
            if (projToId.containsKey(meta.project)) {
                IdParams ip = projToId.get(meta.project);
                ip.id = id;
            }
            else {
                IdParams ip = new IdParams();
                ip.setId(id);
                ip.setParameters(meta.params.stream().map(Parameter::getName).collect(Collectors.toList()));
                projToId.put(meta.project, ip);
            }
        }
        else {
            // In this case, although the XML file existed and we have (some) annotation data, we don't have all
            // of it.  So let's put it into this.mappingFile
            String msg = "XML data exists, but does not exist in mapping file.  Editing map: %s -> {%s: %s}";
            logger.debug(String.format(msg, meta.qualifiedName, meta.project, id));
            TestDefinitionProcessor.setPolarionIDInMapFile(meta.qualifiedName, meta.project, id, mapFile);
        }
    }

    /**
     * Main function that processes metadata annotated on test methods, generating XML description files
     *
     * @param meta a Meta object holding annotation data from a test method
     * @return a Testcase object that can be unmarshalled into XML
     */
    private Testcase processTC(Meta<TestDefinition> meta) throws MismatchError {
        return TestDefinitionProcessor.processTC(meta, this.mappingFile, this.testCaseToMeta, this.tcPath, this.tcMap);
    }

    /**
     * Generates the data in the mapping file as needed and determines if a testcase import request is needed
     *
     * @param meta
     * @param mapFile
     * @param tcToMeta
     * @param testCasePath
     * @param testCaseMap
     * @return
     */
    public static Testcase processTC(Meta<TestDefinition> meta,
                                     Map<String, Map<String, IdParams>> mapFile,
                                     Map<Testcase, Meta<TestDefinition>> tcToMeta,
                                     String testCasePath,
                                     Map<String, List<Testcase>> testCaseMap) {
        Testcase tc = TestDefinitionProcessor.initImporterTestcase(meta, null);
        tcToMeta.put(tc, meta);
        TestDefinition def = meta.annotation;

        // If this method is in the mapping File for the project, and it's not an empty string, we are done
        Optional<String> maybeMapFileID =
                TestDefinitionProcessor.getPolarionIDFromMapFile(meta.qualifiedName, meta.project, mapFile);
        if (maybeMapFileID.isPresent() && !maybeMapFileID.get().equals("")) {
            String mapId = maybeMapFileID.get();
            logger.info(String.format("%s id is %s", meta.qualifiedName, maybeMapFileID.get()));
            // If this testmethod is in the mapping file for this project and it's not set to update, return
            if (meta.polarionID.equals(maybeMapFileID.get())) {
                if (!def.update())
                    return tc;
                else
                    logger.info(String.format("%s is in the mapping file, but an update will be performed",
                            meta.qualifiedName));
            }
            else if (meta.polarionID.equals("")) {
                logger.warn("For %s, in project %s, the testCaseID is an empty string even though the corresponding " +
                        "XML description file is present and has ID = %s", meta.qualifiedName, meta.project, mapId);
            }
            else {
                String err = "Mismatch of TestCaseID: annotation ID = %s, mapping file ID = %s";
                throw new MismatchError(String.format(err, meta.polarionID, maybeMapFileID.get()));
            }
        }

        // Check to see if there is an existing XML description file with Polarion ID
        Optional<File> xml = meta.getFileFromMeta(testCasePath);
        if (!xml.isPresent()) {
            Path path = FileHelper.makeXmlPath(testCasePath, meta, meta.project);
            File xmlDesc = path.toFile();
            TestDefinitionProcessor.createTestCaseXML(tc, xmlDesc);
        }

        JAXBHelper jaxb = new JAXBHelper();
        Optional<String> maybePolarionID = meta.getPolarionIDFromTestcase();
        Optional<String> maybeIDXml = meta.getPolarionIDFromXML(testCasePath);
        Boolean idExists = maybePolarionID.isPresent();
        Boolean xmlIdExists = maybeIDXml.isPresent();
        Boolean importRequest = false;
        //  i | xmlIdExists  | idExists       | action            | How does this happen?
        // ===|==============|================|===================|=============================================
        //  0 | false        | false          | request and edit  | id="" in xml and in annotation
        //  1 | false        | true           | edit XML          | id="" in xml, but id is in annotation
        //  2 | true         | false          | edit mapfile      | non-empty id in xml, but not in annotation
        //  3 | true         | true           | validate          | non-empty id in xml and in annotation
        if (!xmlIdExists)
            importRequest = true;
        if (idExists && !xmlIdExists) {
            // This means that the ID exists in the annotation, but not the XML file.  Edit the xml file
            if (def.update())
                importRequest = true;
            else {
                importRequest = false;
                Optional<Testcase> maybeTC = meta.getTypeFromMeta(Testcase.class, testCasePath);
                if (maybeTC.isPresent()) {
                    Testcase tcase = maybeTC.get();
                    tcase.setId(maybePolarionID.get());
                    Optional<File> path = meta.getFileFromMeta(testCasePath);
                    if (path.isPresent())
                        IJAXBHelper.marshaller(tcase, path.get(), jaxb.getXSDFromResource(Testcase.class));
                }
            }
        }
        if (xmlIdExists && !idExists)
            TestDefinitionProcessor.addToMapFile(mapFile, meta, maybeIDXml.get());
        if (!mapFile.containsKey(meta.qualifiedName)) {
            String id = maybeIDXml.orElseGet(() -> maybePolarionID.orElse(""));
            if (!id.equals(""))
                TestDefinitionProcessor.addToMapFile(mapFile, meta, id);
        }

        if (def.update()) {
            if (xmlIdExists || idExists) {
                String msg = WarningInfo.WarningType.UpdateButIDExists.message();
                WarningInfo.WarningType wt = WarningInfo.WarningType.UpdateButIDExists;
                WarningInfo wi = new WarningInfo(msg, meta.qualifiedName, meta.project, wt);
                warnings.put(meta.qualifiedName, wi);
            }
            importRequest = true;
        }

        if (importRequest) {
            String projId = meta.project;
            if (testCaseMap.containsKey(projId))
                testCaseMap.get(projId).add(tc);
            else {
                List<Testcase> tcs = new ArrayList<>();
                tcs.add(tc);
                testCaseMap.put(projId, tcs);
            }
        }
        return tc;
    }


    /**
     * Creates a simple JSON file which maps a file system location to the Polarion ID
     *
     * Here's a rather complex example of a reduction.  Notice this uses the 3 arg version of reduce.
     * @return
     */
    private void createMappingFile(File mapPath) {
        Map<String, Map<String, IdParams>> tmap = TestDefinitionProcessor.printSortedMappingFile(this.mappingFile);
        TestDefinitionProcessor.createMappingFile(mapPath, this.methToProjectDef, tmap);
    }

    /**
     * Creates a simple JSON file which maps a file system location to the Polarion ID
     *
     * Here's a rather complex example of a reduction.  Notice this uses the 3 arg version of reduce.
     * @return
     */
    public static void createMappingFile(File mapPath,
                                         Map<String, Map<String, Meta<TestDefinition>>> methToProjMeta,
                                         Map<String, Map<String, IdParams>> mapFile) {
        logger.info("Generating mapping file based on all methods");
        HashMap<String, Map<String, IdParams>> collected = new HashMap<>();
        // Iterate through the map of qualifiedMethod -> ProjectID -> Meta<TestDefinition>
        Map<String, Map<String, IdParams>> mpid = methToProjMeta.entrySet().stream()
                .reduce(collected,
                        (accum, entry) -> {
                            String methName = entry.getKey();
                            Map<String, Meta<TestDefinition>> methToDef = entry.getValue();
                            HashMap<String, IdParams> accumulator = new HashMap<>();
                            Map<String, IdParams> methToProject = methToDef.entrySet().stream()
                                    .reduce(accumulator,
                                            (acc, n) -> {
                                                String project = n.getKey();
                                                Meta<TestDefinition> m = n.getValue();
                                                if (mapFile.containsKey(methName)) {
                                                    Map<String, IdParams> pToI = mapFile.get(methName);
                                                    Boolean projectInMapping = pToI.containsKey(project);
                                                    if (projectInMapping) {
                                                        String idForProject = pToI.get(project).id;
                                                        Boolean idIsEmpty = idForProject.equals("");
                                                        if (!idIsEmpty) {
                                                            String msg = "Id for %s is in mapping file";
                                                            logger.debug(msg, idForProject);
                                                            m.polarionID = idForProject;
                                                        }
                                                    }
                                                }
                                                String id = m.polarionID;
                                                List<String> params = m.params.stream()
                                                        .map(Parameter::getName)
                                                        .collect(Collectors.toList());
                                                IdParams ip = new IdParams(id, params);

                                                acc.put(project, ip);
                                                return acc;
                                            },
                                            (a, next) -> {
                                                a.putAll(next);
                                                return a;
                                            });
                            accum.put(methName, methToProject);
                            return accum;
                        },
                        (partial, next) -> {
                            partial.putAll(next);
                            return partial;
                        });
        ObjectMapper mapper = new ObjectMapper();
        // FIXME: Do i still need this?
        for(Map.Entry<String, Map<String, IdParams>> me: mpid.entrySet()) {
            String functionName = me.getKey();
            Map<String, IdParams> val = me.getValue();
            for(Map.Entry<String, IdParams> e: val.entrySet()) {
                String project = e.getKey();
                IdParams params = e.getValue();
                mapFile.get(functionName).put(project, params);
            }
        }
        try {
            mapper.writer().withDefaultPrettyPrinter().writeValue(mapPath, mapFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static Map<String, Map<String, IdParams>> printSortedMappingFile(Map<String, Map<String, IdParams>> defs) {
        Map<String, Map<String, IdParams>> sorted = new TreeMap<>();
        for(Map.Entry<String, Map<String, IdParams>> me: defs.entrySet()) {
            String fnName = me.getKey();
            Map<String, IdParams> projMap = new TreeMap<>(me.getValue());
            sorted.put(fnName, projMap);
        }

        for(Map.Entry<String, Map<String, IdParams>> me: defs.entrySet()) {
            String key = me.getKey();
            Map<String, IdParams> val = me.getValue();
            String fmt = "{\n  %s : {\n    %s : {\n      id : %s,\n      params : %s\n    }\n}";
            for(Map.Entry<String, IdParams> e: val.entrySet()) {
                String project = e.getKey();
                IdParams param = e.getValue();
                String id = param.getId();
                String ps = param.getParameters().stream().reduce("", (acc, n) -> {
                    String total = acc += n + ", ";
                    return total;
                });
                if (ps.length() > 0)
                    ps = String.format("[ %s ]", ps.substring(0, ps.length() - 2));
                else
                    ps = "[ ]";
                System.out.println(String.format(fmt, key, project, id, ps));
            }
        }
        return sorted;
    }

    @Override
    public synchronized void init(ProcessingEnvironment env) {
        System.out.println("In init() method");
        super.init(env);
        this.elements = env.getElementUtils();
        this.msgr = env.getMessager();

        this.methNameToTestNGDescription = new HashMap<>();
        this.methToRequirement = new HashMap<>();
        this.methToProjectReq = new HashMap<>();
        this.testCaseToMeta = new HashMap<>();
        this.config = new XMLConfig(null);
        this.cfg = this.config.config;
        this.tcPath = this.config.getTestcasesXMLPath();
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        Set<String> annotationTypes = new LinkedHashSet<>();
        annotationTypes.add(TestDefinition.class.getCanonicalName());
        annotationTypes.add(Requirement.class.getCanonicalName());
        annotationTypes.add(Test.class.getCanonicalName());
        return annotationTypes;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    private void errorMsg(Element elem, String msg, Object... args) {
        this.msgr.printMessage(Diagnostic.Kind.ERROR, String.format(msg, args), elem);
    }

    private File createGraph() {
        return null;
    }
}
