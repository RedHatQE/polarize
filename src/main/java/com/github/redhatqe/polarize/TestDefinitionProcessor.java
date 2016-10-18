package com.github.redhatqe.polarize;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.redhatqe.polarize.configuration.ConfigType;
import com.github.redhatqe.polarize.configuration.XMLConfig;
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
    private Logger logger;
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
    public JAXBHelper jaxb = new JAXBHelper();
    private Testcases testcases = new Testcases();
    private Map<String, List<Testcase>> tcMap = new HashMap<>();
    private XMLConfig config;
    private ConfigType cfg;

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

    private List<Meta<TestDefinition>>
    makeMetaFromTestDefinitions(List<? extends Element> elements){
        List<Meta<TestDefinition>> metas = new ArrayList<>();
        for(Element e : elements) {
            TestDefinitions container = e.getAnnotation(TestDefinitions.class);
            List<Parameter> params = this.findParameterData(e);
            for(TestDefinition r: container.value()) {
                int i = 0;
                for(Project project: r.projectID()) {
                    String testID = "";
                    if (i < r.testCaseID().length)
                        testID = r.testCaseID()[i];

                    Meta<TestDefinition> m = new Meta<>();
                    m.polarionID = testID;
                    String full = this.getTopLevel(e, "", m);
                    this.logger.info(String.format("Fully qualified name is %s", full));
                    m.qualifiedName = full;
                    m.annotation = r;
                    m.project = project.toString();
                    if (m.params == null)
                        m.params = params;
                    else if (m.params.isEmpty())
                        m.params.addAll(params);
                    metas.add(m);
                }
            }
        }
        return metas;
    }

    private List<Meta<TestDefinition>>
    makeMetaFromTestDefinition(List<? extends Element> elements){
        List<Meta<TestDefinition>> metas = new ArrayList<>();
        for(Element e : elements) {
            List<Parameter> params = this.findParameterData(e);
            TestDefinition def = e.getAnnotation(TestDefinition.class);
            int i = 0;
            for(Project project: def.projectID()) {
                String testID = "";
                if (i < def.testCaseID().length)
                    testID = def.testCaseID()[i];
                Meta<TestDefinition> m = new Meta<>();
                String full = this.getTopLevel(e, "", m);
                this.logger.info(String.format("Fully qualified name is %s", full));
                m.polarionID = testID;
                m.qualifiedName = full;
                m.annotation = def;
                m.project = project.toString();
                if (m.params == null)
                    m.params = params;
                else if (m.params.isEmpty())
                    m.params.addAll(params);
                metas.add(m);
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
        File mapPath = new File(this.cfg.getMapping().getPath());
        System.out.println(mapPath.toString());
        if (mapPath.exists()) {
            System.out.println("Loading the map");
            this.mappingFile = FileHelper.loadMapping(mapPath);
            System.out.println(this.mappingFile.toString());
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

        // Update the mapping file
        if (this.methToProjectDef.size() > 0) {
            System.out.println("Creating mapping file");
            this.createMappingFile(mapPath);
            try {
                Files.lines(Paths.get(mapPath.toString())).forEach(System.out::println);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        else {
            System.out.println("Could not create mapping file");
        }

        /* testcases holds all the methods that need a new or updated Polarion TestCase */
        this.logger.info("Updating testcases that had no testcaseId...");
        this.testcasesImporterRequest();

        this.tcMap = new HashMap<>();
        this.mappingFile = new HashMap<>();
        return true;
    }

    /**
     * Runs processTC on all the entries in methToProjectDef
     *
     * @return
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

    private void createTestCaseXML(Testcase tc,
                                   File path) {
        this.logger.info(String.format("Generating XML description in %s", path.toString()));
        IFileHelper.makeDirs(path.toPath());
        IJAXBHelper.marshaller(tc, path,
                this.jaxb.getXSDFromResource(Testcase.class));
    }

    /**
     * Sets all the information necessary for the testcases object, and marshalls it to an XML file
     *
     * The XML that is created by this call can be used for the sendImportRequest method
     *
     * @param selectorName the response name (part of selector string)
     * @param selectorValue value for the response name (part of selector string)
     * @return
     */
    private Optional<String> initTestcases(String selectorName, String selectorValue) {
        String projectID = this.cfg.getProject();
        if (!this.tcMap.containsKey(projectID)) {
            this.logger.error("Project ID does not exist within Testcase Map");
            return Optional.empty();
        }
        if (this.tcMap.get(projectID).isEmpty()) {
            this.logger.info(String.format("No testcases for %s to import", projectID));
            return Optional.empty();
        }
        this.testcases.setProjectId(projectID);
        this.testcases.setUserId(this.cfg.getAuthor());
        this.testcases.getTestcase().addAll(this.tcMap.get(projectID));

        ResponseProperties respProp = this.testcases.getResponseProperties();
        if (respProp == null)
            respProp = new ResponseProperties();
        this.testcases.setResponseProperties(respProp);
        List<ResponseProperty> props = respProp.getResponseProperty();
        ResponseProperty rprop = new ResponseProperty();
        rprop.setName(selectorName);
        rprop.setValue(selectorValue);
        props.add(rprop);

        File testcaseXml = new File(this.config.testcase.getFile().getPath());
        IJAXBHelper.marshaller(this.testcases, testcaseXml, this.jaxb.getXSDFromResource(Testcases.class));
        return Optional.of(projectID);
    }

    /**
     * FIXME: Need to redo this like the XUnitReporter.sendXunitImportRequest
     * FIXME: Look into guice to inject dependency args (ie, don't look up polarize.properties)
     *
     * Generates the xml file that will be sent via a REST call to the TestCase Importer
     */
    private Optional<ObjectNode> testcasesImporterRequest() {
        Optional<ObjectNode> maybeNode = Optional.empty();
        if (this.tcMap.isEmpty()) {
            this.logger.info("No more testcases to import ...");
            return maybeNode;
        }

        String selectorName = this.config.testcase.getSelector().getName();
        String selectorValue = this.config.testcase.getSelector().getVal();
        String selector = String.format("%s='%s'", selectorName, selectorValue);
        if (!this.initTestcases(selectorName, selectorValue).isPresent())
            return maybeNode;

        try {
            maybeNode = this.sendTCImporterRequest(selector);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        } catch (JMSException e) {
            e.printStackTrace();
        }
        return maybeNode;
    }

    /**
     * Returns a lambda usable as a handler for ImporterRequest.sendImportRequest
     *
     * This handler will take the ObjectNode (for example, decoded from a message on the message bus) gets the Polarion
     * ID from the ObjectNode, and edits the XML file with the Id.  It will also store
     * @return
     */
    public Consumer<Optional<ObjectNode>> testcaseImportHandler() {
        return node -> {
            if (!node.isPresent()) {
                this.logger.warn("No message was received");
                return;
            }
            JsonNode root = node.get().get("root");
            JsonNode testcases = root.get("import-testcases");
            testcases.forEach(n -> {
                String name = n.get("name").textValue();
                if (!n.get("status").textValue().equals("failed")) {
                    String id = n.get("id").toString();
                    if (id.startsWith("\""))
                        id = id.substring(1);
                    if (id.endsWith("\""))
                        id = id.substring(0, id.length() -1);
                    this.logger.info("New Testcase id = " + id);
                    Optional<Path> maybeXML = FileHelper.getXmlPath(this.tcPath, name, this.cfg.getProject());
                    if (!maybeXML.isPresent()) {
                        // In this case, we couldn't get the XML path due to a bad name or tcPath
                        String err = String.format("Couldn't generate XML path for %s and %s", this.tcPath, name);
                        this.logger.error(err);
                        throw new InvalidArgument();
                    }
                    else {
                        Path xmlDefinition = maybeXML.get();
                        File xmlFile = xmlDefinition.toFile();
                        if (!xmlFile.exists()) {
                            this.logger.info("No XML file exists...generating one");
                            Testcase matched = this.findTestcaseByName(name);
                            IJAXBHelper.marshaller(matched, xmlFile, this.jaxb.getXSDFromResource(Testcase.class));
                        }
                        this.logger.debug(String.format("Found %s for method %s", xmlDefinition.toString(), name));
                        this.logger.debug("Unmarshalling to edit the XML file");
                        Testcase tc = XUnitReporter.setPolarionIDFromXML(xmlDefinition.toFile(), id);
                        if (!tc.getId().equals(id)) {
                            this.logger.error("Setting the id for the XML on the Testcase failed");
                            throw new XMLEditError();
                        }
                        IJAXBHelper.marshaller(tc, xmlFile, this.jaxb.getXSDFromResource(Testcase.class));
                    }
                }
                else {
                    this.logger.error(String.format("Unable to create testcase for %s", name));
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
    private Testcase findTestcaseByName(String name) {
        List<Testcase> tcs = this.testcases.getTestcase().stream()
                .filter(tc -> {
                    String title = tc.getTitle();
                    return title.equals(name);
                })
                .collect(Collectors.toList());
        if (tcs.size() != 1) {
            this.logger.error("Found more than one matching qualified name in testcases");
            throw new SizeError();
        }
        return tcs.get(0);
    }

    private Optional<ObjectNode> sendTCImporterRequest(String selector)
            throws InterruptedException, ExecutionException, JMSException {
        String url = this.config.polarion.getUrl() + this.config.testcase.getEndpoint().getRoute();
        File testcaseXml = new File(this.config.testcase.getFile().getPath());
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
                    this.logger.warn(String.format("Project %s already exists for %s", project, meth));
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
     * @param meta
     * @param tc
     */
    private void initTestSteps(Meta<TestDefinition> meta, Testcase tc) {
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

    private String getPolarionIDFromDef(TestDefinition def, String project) {
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
            this.logger.error("The meta.project value not found in TestDefintion.projectID()");
            throw new PolarionMappingError();
        }
        return ids[index];
    }

    private Optional<String> getPolarionIDFromMapFile(String name, String project) {
        Map<String, IdParams> pToID = this.mappingFile.getOrDefault(name, null);
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

    private void setPolarionIDInMapFile(String name, String project, String id) {
        Map<String, IdParams> pToI;
        if (this.mappingFile.containsKey(name)) {
            pToI = this.mappingFile.get(name);
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
        this.mappingFile.put(name, pToI);
    }

    /**
     * FIXME: I think this would have been better implemented as a composition
     *
     * Returns a lambda of a Consumer<DefType.Custom> that can be used to set custom fields
     *
     * @param supp
     * @param def
     * @return
     */
    private Consumer<DefTypes.Custom> customFieldsSetter(Consumer2<String, String> supp, TestDefinition def) {
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
                    this.logger.warn(String.format("Unknown enum value: %s", key.toString()));
            }
        };
    }

    /**
     *
     * @param meta
     * @return
     */
    private Testcase
    initImporterTestcase(Meta<TestDefinition> meta) {
        Testcase tc = new Testcase();
        TestDefinition def = meta.annotation;
        this.initTestSteps(meta, tc);
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

        Consumer<DefTypes.Custom> transformer = this.customFieldsSetter(supp, def);
        for(DefTypes.Custom cust: fieldKeys) {
            transformer.accept(cust);
        }

        if (def.description().equals(""))
            tc.setDescription(this.methNameToTestNGDescription.get(meta.qualifiedName));
        else
            tc.setDescription(def.description());

        if (def.title().equals(""))
            tc.setTitle(meta.qualifiedName);
        else
            tc.setTitle(def.title());

        tc.setId(this.getPolarionIDFromDef(def, meta.project));
        tc.setCustomFields(custom);
        return tc;
    }

    /**
     * Main function that processes metadata annotated on test methods, generating XML description files
     *
     * @param meta a Meta object holding annotation data from a test method
     * @return a Testcase object that can be unmarshalled into XML
     */
    private Testcase processTC(Meta<TestDefinition> meta) {
        Testcase tc = this.initImporterTestcase(meta);
        this.testCaseToMeta.put(tc, meta);
        TestDefinition def = meta.annotation;

        // If this method is in the mapping File for the project, and it's not an empty string, we are done
        Optional<String> maybeMapFileID = this.getPolarionIDFromMapFile(meta.qualifiedName, meta.project);
        if (maybeMapFileID.isPresent() && !maybeMapFileID.get().equals("")) {
            this.logger.info(String.format("%s id is %s", meta.qualifiedName, maybeMapFileID.get()));
            return tc;
        }

        // Check to see if there is an existing XML description file with Polarion ID
        Optional<File> xml = meta.getFileFromMeta(this.tcPath);
        if (!xml.isPresent()) {
            Path path = FileHelper.makeXmlPath(this.tcPath, meta, meta.project);
            File xmlDesc = path.toFile();
            this.createTestCaseXML(tc, xmlDesc);
        }

        Optional<String> maybePolarionID = meta.getPolarionIDFromTestcase();
        Optional<String> maybeIDXml = meta.getPolarionIDFromXML(this.tcPath);
        Boolean idExists = maybePolarionID.isPresent();
        Boolean xmlIdExists = maybeIDXml.isPresent();
        Boolean importRequest = false;
        // i  | xmlIdExists  | idExists       | action            | How does this happen?
        // ===|==============|================|===================|=============================================
        //  0 | false        | false          | request and edit  | id="" in xml and in annotation
        //  1 | false        | true           | edit XML          | id="" in xml, but id is in annotation
        //  2 | true         | false          | edit mapfile      | non-empty id in xml, but not in annotation
        //  3 | true         | true           | validate          | non-empty id in xml and in annotation
        if (!xmlIdExists) {
            importRequest = true;
        }
        if (idExists && !xmlIdExists) {
            // This means that the ID exists in the annotation, but not the XML file.  Edit the xml file
            if (def.update())
                importRequest = true;
            else {
                importRequest = false;
                Optional<Testcase> maybeTC = meta.getTypeFromMeta(Testcase.class, this.tcPath);
                if (maybeTC.isPresent()) {
                    Testcase tcase = maybeTC.get();
                    tcase.setId(maybePolarionID.get());
                    Optional<File> path = meta.getFileFromMeta(this.tcPath);
                    if (path.isPresent()) {
                        IJAXBHelper.marshaller(tcase, path.get(), jaxb.getXSDFromResource(Testcase.class));
                    }
                }
            }
        }
        if (xmlIdExists && !idExists) {
            // TODO: The ID exists in the XML file, but not in the annotation.  Set the mapping file with this info
            Map<String, IdParams> projToId = this.mappingFile.getOrDefault(meta.qualifiedName, null);
            if (projToId != null) {
                if (projToId.containsKey(meta.project)) {
                    IdParams ip = projToId.get(meta.project);
                    ip.id = maybeIDXml.get();
                }
            }
            else {
                // In this case, although the XML file existed and we have (some) annotation data, we don't have all
                // of it.  So let's put it into this.mappingFile
                String msg = "XML data exists, but does not exist in mapping file.  Editing map: %s -> {%s: %s}";
                this.logger.debug(String.format(msg, meta.qualifiedName, meta.project, maybeIDXml.get()));
                this.setPolarionIDInMapFile(meta.qualifiedName, meta.project, maybeIDXml.get());
            }
        }

        if (importRequest) {
            String projId = meta.project;
            if (this.tcMap.containsKey(projId))
                this.tcMap.get(projId).add(tc);
            else {
                List<Testcase> tcs = new ArrayList<>();
                tcs.add(tc);
                this.tcMap.put(projId, tcs);
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
        HashMap<String, Map<String, IdParams>> collected = new HashMap<>();
        // Iterate through the map of qualifiedMethod -> ProjectID -> Meta<TestDefinition>
        Map<String, Map<String, IdParams>> mpid = this.methToProjectDef.entrySet().stream()
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
                                                if (this.mappingFile.containsKey(methName)) {
                                                    Map<String, IdParams> pToI = this.mappingFile.get(methName);
                                                    Boolean projectInMapping = pToI.containsKey(project);
                                                    if (projectInMapping) {
                                                        String idForProject = pToI.get(project).id;
                                                        Boolean idIsEmpty = idForProject.equals("");
                                                        if (!idIsEmpty) {
                                                            this.logger.debug("Id for %s is in mapping file");
                                                            m.polarionID = idForProject;
                                                        }
                                                    }
                                                }
                                                String id = m.polarionID;
                                                List<String> params = m.params.stream()
                                                        .map(Parameter::getName).collect(Collectors.toList());
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
        try {
            mapper.writer().withDefaultPrettyPrinter().writeValue(mapPath, mpid);
            System.out.println(mapper.writer().withDefaultPrettyPrinter().writeValueAsString(mpid));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public synchronized void init(ProcessingEnvironment env) {
        System.out.println("In init() method");
        super.init(env);
        this.elements = env.getElementUtils();
        this.msgr = env.getMessager();
        this.logger = LoggerFactory.getLogger(TestDefinitionProcessor.class);

        this.methNameToTestNGDescription = new HashMap<>();
        this.methToRequirement = new HashMap<>();
        this.methToProjectReq = new HashMap<>();
        this.testCaseToMeta = new HashMap<>();
        this.config = new XMLConfig(null);
        this.cfg = this.config.config;
        this.tcPath = this.cfg.getTestcasesXml().getPath();
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        Set<String> anns = new LinkedHashSet<>();
        anns.add(TestDefinition.class.getCanonicalName());
        anns.add(Requirement.class.getCanonicalName());
        anns.add(Test.class.getCanonicalName());
        return anns;
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
