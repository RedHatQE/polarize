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
import com.github.redhatqe.polarize.utils.Tuple;
import org.testng.annotations.Test;


import javax.annotation.processing.*;
import javax.jms.JMSException;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;
import java.io.*;
import java.lang.annotation.Annotation;
import java.nio.file.*;
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
    private Map<String, Map<String, Meta<TestDefinition>>> methToProjectDef;
    private Map<String, String> methNameToTestNGDescription;
    private Map<Testcase, Meta<TestDefinition>> testCaseToMeta;
    // Map of qualified name -> { projectID: testcaseID }
    private Map<String, Map<String, IdParams>> mappingFile = new LinkedHashMap<>();
    public static JAXBHelper jaxb = new JAXBHelper();
    private Testcases testcases = new Testcases();
    private Map<String, List<Testcase>> tcMap = new HashMap<>();
    private XMLConfig config;
    private static Map<String, WarningInfo> warnings = new HashMap<>();
    private int round = 0;

    public static final String warnText = "/tmp/polarize-warnings.txt";
    public static final String tempTestCase = "/tmp/testcases-%s.xml";

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
     * @param meta The TestDefinition object to check
     * @param name name of the project
     */
    private void validateProjectToTestCase(TestDefinition meta, String name) {
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
     * @param elements A List of objects that derive from Element
     * @return A List of Meta\<TestDefinition\>
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
        m.qualifiedName = this.getTopLevel(e, "", m);
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

    /**
     * Creates a list of Meta of type TestDefinition from every method annoted with @TestDefinition
     *
     * This function is called at compile time by the process() function.  For every method which is annotated with the
     * TestDefinition, it will be included in the argument <i>elements</i>.  From this, the actual annotation object
     * can be retrieved.
     *
     * @param elements A list of Element from all methods which have been annotated by TestDefinition
     * @return list of Meta of type TestDefinition
     */
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
     * The function which creates all the XML definition files based on source annotations
     *
     * The TestDefinitionProcessor actually needs to look for three annotation types:
     * <ul>
     *   <li>{@link TestDefinition}: to get TestDefinition WorkItem information</li>
     *   <li>{@link Requirement}: to get Requirement WorkItem information</li>
     *   <li>{@link Test}: to get the existing description</li>
     * </ul>
     *
     * This function is called implicitly by the compiler and should not be called from user code.
     *
     * @param set passed from compiler
     * @param roundEnvironment passed from compiler
     * @return true if processed successfully, false otherwise
     */
    @Override
    public boolean process(Set<? extends TypeElement> set, RoundEnvironment roundEnvironment) {
        if (this.round > 0) {
            logger.info("Done with annotation processing");
            String msg = "Don't forget to set <enabled>false</enabled> under the <importer type=\"testcase\"> " +
                    "section of your xml-config.xml file when you are done creating/updating testcases in Polarion.";
            if (isUpdateSet(this.config, "testcase"))
                logger.info(msg);
            return true;
        }

        if (this.config.config == null) {
            logger.info("=====================================================");
            logger.info("No config file found...skipping annotation processing");
            logger.info("=====================================================");
            return true;
        }

        // load the mapping file
        File mapPath = new File(this.config.getMappingPath());
        System.out.println(mapPath.toString());
        if (mapPath.exists()) {
            logger.info("Loading the map");
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

        this.processAllTC();

        /* testcases holds all the methods that need a new or updated Polarion TestCase */
        this.tcImportRequest();
        File mapjsonPath = new File(this.config.getMappingPath());
        TestDefinitionProcessor.updateMappingFile(this.mappingFile, this.methToProjectDef, this.tcPath, mapjsonPath);

        /* Generate the mapping file now that all the XML files should have been generated */
        if (!mapjsonPath.exists()) {
            this.mappingFile = this.createMappingFile(mapPath);
        }

        this.printWarnings(warnings);
        this.tcMap = new HashMap<>();
        this.mappingFile = new HashMap<>();

        Tuple<Set<String>, List<UpdateAnnotation>> audit =
                this.auditMethods(this.methNameToTestNGDescription, this.methToProjectDef);

        this.round += 1;
        return true;
    }

    private void printWarnings(Map<String, WarningInfo> warns) {
        if (this.tcMap.isEmpty()) {
            String warnMsg = warns.entrySet().stream()
                    .map(es -> {
                        String meth = es.getKey();
                        WarningInfo wi = es.getValue();
                        return String.format("In %s- %s : %s", meth, wi.project, wi.wt.message());
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
                    "before using your project to create Polarion TestRuns.  Please see the file " +
                    String.format("%s for all the functions to check", warnText));
        try {
            // FIXME: rotate the TestDefinitionProcess.errorsText
            Path bf = Paths.get(warnText);
            StandardOpenOption opt = bf.toFile().exists() ? StandardOpenOption.APPEND : StandardOpenOption.CREATE;
            Files.write(bf, badFunctions, opt);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void updateMappingFile(Map<String, Map<String, IdParams>> mapFile,
                                         Map<String, Map<String, Meta<TestDefinition>>> methMap,
                                         String tcpath,
                                         File mapPath) {
        List<String> badFunctions = new ArrayList<>();
        methMap.forEach((fnName, projectToMeta) -> projectToMeta.forEach((project, meta) -> {
            String id = meta.getPolarionIDFromTestcase()
                    .orElseGet(() -> {
                        Tuple<String, Testcase> maybe =
                                meta.getPolarionIDFromXML(tcpath).orElse(new Tuple<>("", null));
                        return maybe.first;
                    });
            Boolean badFn = false;
            // Check if the mapFile has the corresponding project of this function name
            if (!mapFile.containsKey(fnName) || !mapFile.get(fnName).containsKey(project)) {
                if (id.equals("")) badFn = true;
            } else {
                String newid = mapFile.get(fnName).get(project).getId();
                if (newid.equals("")) badFn = true;
                if (id.equals("")) id = newid;
            }

            if (badFn)
                TestDefinitionProcessor.addBadFunction(meta.qualifiedName, project, badFunctions);
            TestDefinitionProcessor.addToMapFile(mapFile, meta, id, mapPath);
        }));
        TestDefinitionProcessor.writeBadFunctionText(badFunctions);
    }

    /**
     * Runs processTC on all the entries in methToProjectDef
     *
     * @return List of all the processed TestCases
     */
    private List<Testcase> processAllTC() {
        File badFuncs = Paths.get(warnText).toFile();
        if (badFuncs.exists())
            badFuncs.delete();
        else
            IFileHelper.makeDirs(badFuncs.toPath());
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
        return TestDefinitionProcessor.initTestcases(selectorName, selectorValue, pID, testXml, this.tcMap,
                this.testcases);
    }

    /**
     * Initializes the Testcases object and returns an optional project ID
     *
     * @param selectorName name part of selector
     * @param selectorValue value part of selector (eg <name>='<value>')
     * @param projectID project ID of the Testcases object
     * @param testcaseXml File to where the Testcases object will be marshalled to
     * @param testMap a map of projectID to Testcase list
     * @param tests the Testcases object that will be initialized
     * @return an optional of the Testcases project
     */
    public static Optional<String>
    initTestcases(String selectorName,
                  String selectorValue,
                  String projectID,
                  File testcaseXml,
                  Map<String, List<Testcase>> testMap,
                  Testcases tests) {
        if (!testMap.containsKey(projectID)) {
            logger.error("ProjectType ID does not exist within Testcase Map");
            return Optional.empty();
        }
        if (testMap.get(projectID).isEmpty()) {
            logger.info(String.format("No testcases for %s to import", projectID));
            return Optional.empty();
        }
        tests.setProjectId(projectID);
        tests.getTestcase().addAll(testMap.get(projectID));

        ResponseProperties respProp = tests.getResponseProperties();
        if (respProp == null)
            respProp = new ResponseProperties();
        tests.setResponseProperties(respProp);
        List<ResponseProperty> props = respProp.getResponseProperty();
        if (props.stream().noneMatch(p -> p.getName().equals(selectorName) && p.getValue().equals(selectorValue))) {
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
     * @param url
     * @param user
     * @param pw
     * @param tests
     * @return
     */
    public static List<Optional<ObjectNode>>
    tcImportRequest(Map<String, List<Testcase>> testcaseMap,
                    String selectorName,
                    String selectorValue,
                    String url,
                    String user,
                    String pw,
                    Testcases tests,
                    String tcPath,
                    TitleType tType) {
        List<Optional<ObjectNode>> maybeNodes = new ArrayList<>();
        if (testcaseMap.isEmpty())
            return maybeNodes;

        String selector = String.format("%s='%s'", selectorName, selectorValue);
        for(String project: testcaseMap.keySet()) {
            String path = String.format(tempTestCase, project);
            File testXml = new File(path);
            if (!TestDefinitionProcessor.initTestcases(selectorName, selectorValue, project, testXml,
                    testcaseMap, tests).isPresent())
                maybeNodes.add(Optional.empty());
            else {
                try {
                    Consumer<Optional<ObjectNode>> hdlr;
                    hdlr = TestDefinitionProcessor.testcaseImportHandler(tcPath, project, tests, tType);
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
            logger.info("No testcases to import ...");
            return maybeNodes;
        }
        logger.info("Updating testcases that had no testcaseId...");

        for(String project: this.tcMap.keySet()) {
            String selectorName = this.config.testcase.getSelector().getName();
            String selectorValue = this.config.testcase.getSelector().getVal();
            String selector = String.format("%s='%s'", selectorName, selectorValue);
            String path = String.format(tempTestCase, project);
            File testXml = new File(path);
            if (!this.initTestcases(selectorName, selectorValue, project, testXml).isPresent())
                maybeNodes.add(Optional.empty());
            else {
                try {
                    maybeNodes.add(this.sendTCImporterRequest(selector, testXml, project));
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
    public static Consumer<Optional<ObjectNode>>
    testcaseImportHandler(String testPath, String pID, Testcases tcs, TitleType tt) {
        return node -> {
            if (node == null || !node.isPresent()) {
                logger.warn("No message was received");
                return;
            }
            JsonNode root = node.get().get("root");
            if (root.has("status")) {
                if (root.get("status").textValue().equals("failed"))
                    return;
            }

            JsonNode testcases = root.get("import-testcases");
            String pf = tt.getPrefix();
            String sf = tt.getSuffix();
            testcases.forEach(n -> {
                // Take off the prefix and suffix from the testcase
                String name = n.get("name").textValue();
                name = name.replace(pf, "");
                name = name.replace(sf, "");

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
                        // FIXME: I need to get the data from the Meta, not XML.
                        Testcase tc = XUnitReporter.setPolarionIDFromXML(xmlDefinition.toFile(), id);
                        if (!tc.getId().equals(id)) {
                            logger.error("Setting the id for the XML on the Testcase failed");
                            throw new XMLEditError();
                        }
                        createTestCaseXML(tc, xmlFile);
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

    private Optional<ObjectNode> sendTCImporterRequest(String selector, File testcaseXml, String project)
            throws InterruptedException, ExecutionException, JMSException {
        if (!this.config.testcase.isEnabled())
            return Optional.empty();
        String baseUrl;
        String user;
        String pw;
        if (testcaseXml.toString().contains("PLATTP")) {
            baseUrl = this.config.polarionDevel.getUrl();
            user = this.config.polarionDevel.getUser();
            pw = this.config.polarionDevel.getPassword();
        }
        else {
            baseUrl = this.config.polarion.getUrl();
            user = this.config.polarion.getUser();
            pw = this.config.polarion.getPassword();
        }
        String url = baseUrl + this.config.testcase.getEndpoint().getRoute();
        TitleType tt = this.config.testcase.getTitle();
        Consumer<Optional<ObjectNode>> hdlr = testcaseImportHandler(this.tcPath, project, this.testcases, tt);
        return ImporterRequest.sendImportRequest(url, user, pw, testcaseXml, selector, hdlr);
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
            }
        }
        return methods;
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

    class UpdateAnnotation {
        public String qualName;
        public String project;
        public Boolean update;

        public UpdateAnnotation(String q, String p, Boolean u) {
            this.qualName = q;
            this.project = p;
            this.update = u;
        }

        @Override
        public String toString() {
            return String.format("Method name: %s, Project: %s, Update: %b", qualName, project, update);
        }
    }

    /**
     * This method does several things.
     * - Check if there is a method annotated with @Test, but not @TestDefinition
     * - Checks if a method's TestDefinition.update = true
     * @return
     */
    private Tuple<Set<String>, List<UpdateAnnotation>>
    auditMethods(Map<String, String> atTests, Map<String, Map<String, Meta<TestDefinition>>> atTD) {
        Set<String> atTestMethods = atTests.keySet();
        Set<String> atTDMethods = atTD.keySet();
        // The set of methods which are annotated with @Test but not with @TestDefinition
        Set<String> difference = atTestMethods.stream()
                .filter(e -> !atTDMethods.contains(e))
                .collect(Collectors.toSet());

        List<UpdateAnnotation> updateAnnotation = atTD.entrySet().stream()
                .flatMap(es -> {
                    String methname = es.getKey();
                    return es.getValue().entrySet().stream()
                            .map(es2 -> {
                                String project = es2.getKey();
                                Meta<TestDefinition> meta = es2.getValue();
                                return new UpdateAnnotation(methname, project, meta.annotation.update());
                            })
                            .collect(Collectors.toList())
                            .stream();
                })
                .filter(na -> na.update)
                .collect(Collectors.toList());
        return new Tuple<>(difference, updateAnnotation);
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
    setPolarionIDInMapFile(Meta<TestDefinition> meta, String id, Map<String, Map<String, IdParams>> mapFile) {
        String name = meta.qualifiedName;
        String project = meta.project;
        Map<String, IdParams> pToI;
        if (mapFile.containsKey(name)) {
            pToI = mapFile.get(name);
        }
        else {
            pToI = new LinkedHashMap<>();
        }

        IdParams ip = pToI.getOrDefault(project, null);
        if (ip != null) {
            ip.id = id;
        }
        else {
            List<String> params;
            if (meta.params != null) {
                params = meta.params.stream().map(Parameter::getName).collect(Collectors.toList());
            }
            else
                params = new ArrayList<>();
            ip = new IdParams(id, params);
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
    public static Testcase
    initImporterTestcase(Meta<TestDefinition> meta, Map<String, String> methToDesc, XMLConfig cfg) {
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

        TitleType titleType = cfg.testcase.getTitle();
        String t = "%s%s%s";
        String title;
        if (def.title().equals("")) {
            title = String.format(t, titleType.getPrefix(), meta.qualifiedName, titleType.getSuffix());
            tc.setTitle(title);
        }
        else {
            title = String.format(t, titleType.getPrefix(), def.title(), titleType.getSuffix());
            tc.setTitle(title);
        }

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
    public static void addToMapFile(Map<String, Map<String, IdParams>> mapFile,
                                    Meta<TestDefinition> meta,
                                    String id,
                                    File mapPath) {
        String msg = "Adding TestCase ID to the mapping file.  Editing map: %s -> {%s: %s}";
        logger.debug(String.format(msg, meta.qualifiedName, meta.project, id));
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
            TestDefinitionProcessor.setPolarionIDInMapFile(meta, id, mapFile);
        }
        writeMapFile(mapPath, mapFile);
    }

    private enum IDType {
        NONE, MAP, XML, XML_MAP, ANN, ANN_MAP, ANN_XML, ALL;

        public static IDType fromNumber(int val) {
            switch (val) {
                case 0:
                    return NONE;
                case 1:
                    return MAP;
                case 2:
                    return XML;
                case 3:
                    return XML_MAP;
                case 4:
                    return ANN;
                case 5:
                    return ANN_MAP;
                case 6:
                    return ANN_XML;
                case 7:
                    return ALL;
                default:
                    return null;
            }
        }
    }

    private static void editXML(Meta<TestDefinition> meta, String xmlPath, String id, Testcase tc) {
        if (!tc.getId().equals(id)) {
            tc.setId(id);
        }
        Optional<File> path = meta.getFileFromMeta(xmlPath);
        path.ifPresent(file -> IJAXBHelper.marshaller(tc, file, jaxb.getXSDFromResource(Testcase.class)));
        if (!path.isPresent() || tc.getDescription() == null || tc.getDescription().equals("")) {
            createTestCaseXML(tc, FileHelper.makeXmlPath(xmlPath, meta).toFile());
        }
    }

    private static void addDescToXML(Meta<TestDefinition> meta, String xmlPath, String desc, Testcase tc) {
        if (tc.getDescription() == null || !tc.getDescription().equals(desc)) {
            tc.setDescription(desc);
        }
        Optional<File> path = meta.getFileFromMeta(xmlPath);
        path.ifPresent(file -> IJAXBHelper.marshaller(tc, file, jaxb.getXSDFromResource(Testcase.class)));
        if (!path.isPresent() || tc.getDescription() == null || tc.getDescription().equals("")) {
            createTestCaseXML(tc, FileHelper.makeXmlPath(xmlPath, meta).toFile());
        }
    }

    private static Boolean verifyEquality(String[] ids, String id) {
        return Arrays.stream(ids).allMatch(n -> n.equals(id));
    }

    private static Map<String, String> nonMatchingIds(Map<String, String> ids, String id) {
        return ids.entrySet().stream()
                .filter(i -> i.getValue().equals(id))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }


    /**
     * Does what is needed based on whether the id exists in the annotation, xml or map file
     *
     * One of the big complications of polarize is that it has to maintain consistency across 3 separate entities for
     * the ID:  the xml definition file, the annotation in the source code, and the mapping file (for the matching
     * project ID).  The order of precedence for determining which ID to use is annotation, XML and the mapping file.
     * .  If there is a discrepancy between 2 or more of the entities, the higher precedence wins, and the lower
     * entities must be changed. The logic for checking this is as follows:
     *
     * | annotation  | xml      | mapping   | Action(s)                                   | Name      |
     * |-------------|----------|-----------|---------------------------------------------|-----------|
     * | 0           | 0        | 0         | Make import request                         | NONE
     * | 0           | 0        | 1         | Edit the XML file, add to badFunction       | MAP
     * | 0           | 1        | 0         | Edit the Mapping file, add to badFunction   | XML
     * | 0           | 1        | 1         | Verify equality, add to badFunction         | XML_MAP
     * | 1           | 0        | 0         | Edit the XML and mapping file               | ANN
     * | 1           | 0        | 1         | Verify equality, edit the XML               | ANN_MAP
     * | 1           | 1        | 0         | Verify equality, add to mapping             | ANN_XML
     * | 1           | 1        | 1         | Verify equality                             | ALL
     *
     * Note that this returns an integer value representing one of 4 possible states (really 3)
     *
     * | update?  | NONE? | Action
     * |----------|-------|-------------
     * | 0        | 0     | No update for this Testcase
     * | 0        | 1     | Make import request, and edit or create XML file for method
     * | 1        | 0     | Make import request, and edit XML for method
     * | 1        | 1     | Make import request, and edit or create XML file for method
     *
     * @param meta A Meta of type TestDefinition which holds the annotation information for the method
     * @param testCasePath A path to look up the XML definition for the method
     * @param mapFile A Map of qualified method -> project -> Polarion ID
     * @param tc the XML representation of the TestCase
     * @param mapPath Path to the mapping.json file (which is the persistent representation of mapFile)
     * @return An int (as binary) representing the 4 possible states for importing
     */
    private static int
    processIdEntities(Meta<TestDefinition> meta,
                      String testCasePath,
                      Map<String, Map<String, IdParams>> mapFile,
                      Testcase tc,
                      File mapPath) {
        List<String> badFuncs = new ArrayList<>();

        Optional<String> maybePolarionID = meta.getPolarionIDFromTestcase();
        Optional<Tuple<String, Testcase>> maybeIDXml = meta.getPolarionIDFromXML(testCasePath);
        Optional<String> maybeMapFileID =
                TestDefinitionProcessor.getPolarionIDFromMapFile(meta.qualifiedName, meta.project, mapFile);
        String annId = maybePolarionID.orElse("");
        Tuple<String, Testcase> idAndTC = maybeIDXml.orElse(new Tuple<>("", null));
        String xmlId = idAndTC.first;
        String mapId = maybeMapFileID.orElse("");
        int importType = meta.annotation.update() ? 1 << 1 : 0;

        // w00t, bit tricks.  Thought I wouldn't need these again after my embedded days :)
        int idval = (annId.equals("") ? 0 : 1 << 2) | (xmlId.equals("") ? 0 : 1 << 1) | (mapId.equals("") ? 0 : 1);
        IDType idtype = IDType.fromNumber(idval);
        if (idtype == null)
            throw new MappingError("Error in IDType.fromNumber()");

        String msg = "In %s the @TestDefinition for project %s: the testCaseID is an empty string even though the " +
                     "corresponding %s is present and has ID = %s.";
        String qual = meta.qualifiedName;
        String project = meta.project;
        String pqual = meta.project + " -> " + qual;

        // Check that the description field is not empty
        if (idAndTC.second != null && tc.getDescription() != null && tc.getDescription().equals("")) {
            String desc = tc.getDescription();
            TestDefinitionProcessor.addDescToXML(meta, testCasePath, desc, idAndTC.second);
        }

        // TODO: Instead of throwing an error on mismatch, perhaps we should auto-correct based on precedence
        // FIXME: When query ability is added, can run a check
        String m = "Adding TestCase ID to the mapping file.  Editing map: %s -> {%s: %s}";
        switch (idtype) {
            case NONE:
                importType |= 0x01;
                break;
            case MAP:
                editXML(meta, testCasePath, mapId, tc);
                badFuncs.add(String.format(msg, qual, project, "Map file", mapId));
                break;
            case XML:
                logger.info(String.format(m, meta.qualifiedName, meta.project, xmlId));
                addToMapFile(mapFile, meta, xmlId, mapPath);
                badFuncs.add(String.format(msg, qual, project, "XML file", xmlId));
                break;
            case XML_MAP:
                String[] idsToCheck = {xmlId, mapId};
                if (!verifyEquality(idsToCheck, xmlId)) {
                    String err = String.format("XML Id = %s and Map ID = %s do not match in %s", xmlId, mapId, pqual);
                    throw new MismatchError(err);
                }
                badFuncs.add(String.format(msg, qual, project, "XML file", mapId));
                break;
            case ANN:
                editXML(meta, testCasePath, annId, tc);
                logger.info(String.format(m, meta.qualifiedName, meta.project, annId));
                addToMapFile(mapFile, meta, annId, mapPath);
                break;
            case ANN_MAP:
                String[] annMapIds = {annId, mapId};
                if (!verifyEquality(annMapIds, annId)) {
                    String fmt = "Annotation ID = %s and Map ID = %s do not match in %s";
                    String err = String.format(fmt, annId, mapId, pqual);
                    throw new MismatchError(err);
                }
                editXML(meta, testCasePath, annId, tc);
                break;
            case ANN_XML:
                String[] annXmlIds = {annId, xmlId};
                if (!verifyEquality(annXmlIds, annId)) {
                    String fmt = "Annotation ID = %s and XML ID = %s do not match in %s";
                    String err = String.format(fmt, annId, xmlId, pqual);
                    throw new MismatchError(err);
                }
                logger.info(String.format(m, meta.qualifiedName, meta.project, annId));
                addToMapFile(mapFile, meta, annId, mapPath);
                break;
            case ALL:
                String[] all = {annId, xmlId, mapId};
                if (!verifyEquality(all, annId)) {
                    Map<String, String> allIds = new HashMap<>();
                    allIds.put("annotation", annId);
                    allIds.put("xml", xmlId);
                    allIds.put("map", mapId);
                    Map<String, String> unmatched = nonMatchingIds(allIds, annId);
                    unmatched.forEach((key, value) -> {
                        String err = "%s id = %s did not match %s in %s";
                        logger.error(String.format(err, key, value, annId, pqual));
                    });
                }
                break;
            default:
                logger.error("Should not get here");
        }
        TestDefinitionProcessor.writeBadFunctionText(badFuncs);
        return importType;
    }


    /**
     * Main function that processes metadata annotated on test methods, generating XML description files
     *
     * @param meta a Meta object holding annotation data from a test method
     * @return a Testcase object that can be unmarshalled into XML
     */
    private Testcase processTC(Meta<TestDefinition> meta) throws MismatchError {
        return TestDefinitionProcessor.processTC(meta, this.mappingFile, this.testCaseToMeta, this.tcPath, this.tcMap,
                new File(this.config.getMappingPath()), this.methNameToTestNGDescription, this.config);
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
                                     Map<String, List<Testcase>> testCaseMap,
                                     File mapPath,
                                     Map<String, String> methToDesc,
                                     XMLConfig config) {
        Testcase tc = TestDefinitionProcessor.initImporterTestcase(meta, methToDesc, config);
        // Check if testCasePath exists.  If it doesn't, generate the XML definition.
        Path xmlDef = FileHelper.makeXmlPath(testCasePath, meta);
        if (!xmlDef.toFile().exists())
            createTestCaseXML(tc, xmlDef.toFile());

        tcToMeta.put(tc, meta);

        int importType = processIdEntities(meta, testCasePath, mapFile, tc, mapPath);
        // If update bit is set, regenerate the XML file with the new data
        if ((importType & 0b10) == 0b10) {
            createTestCaseXML(tc, xmlDef.toFile());
        }

        // If the update bit and the none bit are 0 we don't do anything.  Otherwise, do an import request
        if (importType != 0) {
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
    private Map<String, Map<String, IdParams>> createMappingFile(File mapPath) {
        Map<String, Map<String, IdParams>> tmap = TestDefinitionProcessor.printSortedMappingFile(this.mappingFile);
        return TestDefinitionProcessor.createMappingFile(mapPath, this.methToProjectDef, tmap);
    }

    /**
     * Creates a simple JSON file which maps a file system location to the Polarion ID
     *
     * Here's a rather complex example of a reduction.  Notice this uses the 3 arg version of reduce.
     * @return
     */
    public static Map<String, Map<String, IdParams>>
    createMappingFile(File mapPath,
                      Map<String, Map<String, Meta<TestDefinition>>> methToProjMeta,
                      Map<String, Map<String, IdParams>> mapFile) {
        logger.info("Generating mapping file based on all methods");
        HashMap<String, Map<String, IdParams>> collected = new HashMap<>();
        // Iterate through the map of qualifiedMethod -> ProjectID -> Meta<TestDefinition>
        Map<String, Map<String, IdParams>> mpid = methToProjMeta.entrySet().stream()
                .reduce(collected,
                        // Function that gets the inner map in methToProjMeta
                        (accum, entry) -> {
                            String methName = entry.getKey();
                            Map<String, Meta<TestDefinition>> methToDef = entry.getValue();
                            HashMap<String, IdParams> accumulator = new HashMap<>();
                            Map<String, IdParams> methToProject = methToDef.entrySet().stream()
                                    .reduce(accumulator,  // our "identity" value is the accumulator
                                            // Gets the map of String -> Meta<TestDefinition> inside methToProjMeta
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
                                                            logger.debug(String.format(msg, idForProject));
                                                            m.polarionID = idForProject;
                                                        }
                                                        else
                                                            throw new MappingError("No ID for " + methName);
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
        writeMapFile(mapPath, mpid);
        return mpid;
    }

    /**
     * Creates the mapping JSON file given a Map of methodName -> Project -> IdParam
     *
     * @param mapPath path for where to write the JSON mapping
     * @param mpid a map of methodName to Project to IdParam object
     */
    public static void writeMapFile(File mapPath, Map<String, Map<String, IdParams>> mpid) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            mapper.writer().withDefaultPrettyPrinter().writeValue(mapPath, printSortedMappingFile(mpid));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static Map<String, Map<String, IdParams>> printSortedMappingFile(Map<String, Map<String, IdParams>> defs) {
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
                    acc += n + ", ";
                    return acc;
                });
                if (ps.length() > 0)
                    ps = String.format("[ %s ]", ps.substring(0, ps.length() - 2));
                else
                    ps = "[ ]";
                //logger.debug(String.format(fmt, key, project, id, ps));
            }
        }
        return sorted;
    }

    /**
     * Checks if the \<enabled\> section is set to true for an importer
     * @return
     */
    private static Boolean isUpdateSet(XMLConfig XCfg, String importerType) {
        switch(importerType) {
            case "testcase":
                ImporterType TC = XCfg.testcase;
                return TC.isEnabled();
            case "xunit":
                ImporterType XU = XCfg.xunit;
                return XU.isEnabled();
            default:
                logger.error("Unknown importer type, returning false");
                return false;
        }
    }

    @Override
    public synchronized void init(ProcessingEnvironment env) {
        super.init(env);
        this.elements = env.getElementUtils();
        this.msgr = env.getMessager();

        this.methNameToTestNGDescription = new HashMap<>();
        this.testCaseToMeta = new HashMap<>();
        this.config = new XMLConfig(null);
        ConfigType cfg = this.config.config;
        if (cfg != null)
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
}
