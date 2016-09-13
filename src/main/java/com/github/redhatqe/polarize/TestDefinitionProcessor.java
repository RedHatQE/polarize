package com.github.redhatqe.polarize;

import com.github.redhatqe.polarize.exceptions.*;
import com.github.redhatqe.polarize.importer.ImporterRequest;
import com.github.redhatqe.polarize.importer.testcase.*;
import com.github.redhatqe.polarize.junitreporter.ReporterConfig;
import com.github.redhatqe.polarize.junitreporter.XUnitReporter;
import com.github.redhatqe.polarize.metadata.*;
import com.github.redhatqe.polarize.schema.*;

import com.github.redhatqe.polarize.importer.testcase.Testcase;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.testng.annotations.Test;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;
import java.io.*;
import java.lang.annotation.Annotation;
import java.net.URL;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


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
    private String reqPath;
    private String tcPath;
    private Map<String, Meta<Requirement>> methToRequirement;
    // map of qualified name -> {projectID: meta}
    private Map<String, Map<String,
                            Meta<Requirement>>> methToProjectReq;
    private Map<String, Map<String,
                            Meta<TestDefinition>>> methToProjectDef;
    private Map<String, String> methNameToTestNGDescription;
    private Map<Testcase, Meta<TestDefinition>> testCaseToMeta;
    public JAXBHelper jaxb = new JAXBHelper();
    private Testcases testcases = new Testcases();
    private ReporterConfig config = XUnitReporter.configure();
    private Map<String, String> polarizeConfig = Configurator.loadConfiguration();
    private Map<String, List<Testcase>> tcMap = new HashMap<>();

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

    /**
     * Creates a List of Meta types from a Requirements annotation
     *
     * @param elements a list of Elements
     * @return a list of Metas of type Requirement
     */
    private List<Meta<Requirement>>
    makeMetaFromRequirements(List<? extends Element> elements){
        List<Meta<Requirement>> metas = new ArrayList<>();
        for(Element e : elements) {
            Requirements container = e.getAnnotation(Requirements.class);
            for(Requirement r: container.value()) {
                Meta<Requirement> m = new Meta<>();
                m.qualifiedName = this.getTopLevel(e, "", m);
                m.annotation = r;
                metas.add(m);
            }
        }
        return metas;
    }

    private <T> List<Meta<T>>
    makeMetaFromPolarions(List<? extends Element> elements,
                          Class<? extends Annotation> ann){
        List<Meta<T>> metas = new ArrayList<>();
        for(Element e : elements) {
            TestDefinitions container = (TestDefinitions) e.getAnnotation(ann);
            for(TestDefinition r: container.value()) {
                Meta m = new Meta<T>();
                String full = this.getTopLevel(e, "", m);
                this.logger.info(String.format("Fully qualified name is %s", full));
                m.qualifiedName = full;
                m.annotation = r;
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

        this.logger.info("Getting all the @Requirement annotations which have been repeated");
        TreeSet<ElementKind> allowed = new TreeSet<>();
        allowed.add(ElementKind.CLASS);
        String err = "Can only annotate classes with @Requirements";
        List<? extends Element> repeatedAnns = this.getAnnotations(roundEnvironment, Requirements.class, allowed, err);
        List<Meta<Requirement>> requirements = this.makeMetaFromRequirements(repeatedAnns);

        /* ************************************************************************************
         * Get all the @Requirement annotated on a class that are not repeated. We will use the information here to
         * generate the XML for requirements.  If a @TestDefinition annotated test method has an empty reqs, we will
         * look in methToRequirements for the associated Requirement.
         **************************************************************************************/
        allowed = new TreeSet<>();
        allowed.add(ElementKind.CLASS);
        allowed.add(ElementKind.METHOD);
        err = "Can only annotate classes or methods with @Requirement";
        List<? extends Element> reqAnns = this.getAnnotations(roundEnvironment, Requirement.class, allowed, err);
        requirements.addAll(this.makeMeta(reqAnns, Requirement.class));
        this.methToProjectReq.putAll(this.createMethodToMetaRequirementMap(requirements));

        /* ************************************************************************************
         * Get all the @TestDefinition annotations which were annotated on an element only once.
         * Make a list of Meta types that store the fully qualified name of every @TestDefinition annotated
         * method.  We will use this to create a map of qualified_name => TestDefinition Annotation
         **************************************************************************************/
        allowed = new TreeSet<>();
        allowed.add(ElementKind.METHOD);
        err = "Can only annotate methods with @TestDefinition";
        List<? extends Element> polAnns = this.getAnnotations(roundEnvironment, TestDefinition.class, allowed, err);
        List<Meta<TestDefinition>> metas = this.makeMeta(polAnns, TestDefinition.class);

        /* Get all the @TestDefinition annotations which have been repeated on a single element. */
        List<? extends Element> pols = this.getAnnotations(roundEnvironment, TestDefinitions.class, allowed, err);
        metas.addAll(this.makeMetaFromPolarions(pols, TestDefinitions.class));

        this.methToProjectDef = this.createMethodToMetaPolarionMap(metas);

        /* Get all the @Test annotations in order to get the description */
        Map<String, String> methToDescription = this.getTestAnnotations(roundEnvironment);
        this.methNameToTestNGDescription.putAll(methToDescription);

        List<ReqType> reqList = this.processAllRequirements();
        if (reqList == null)
            return false;
        List<Testcase> tests = this.processAllTC();

        /* testcases holds all the methods that need a new or updated Polarion TestCase */
        this.logger.info("Updating testcases that had <update>...");
        Map<Testcase, Update> updated = this.testcasesImporterRequest();
        this.testcases.getTestcase().forEach(
                tc -> {
                    if(updated.containsKey(tc)) {
                        tc.setUpdate(updated.get(tc));
                        if (tc.getCreate() != null)
                            tc.setCreate(null);
                    }
                }
        );
        this.logger.info("Regenerating testcases...");
        List<Testcase> testcases = this.testcases.getTestcase();
        for (Testcase tc: testcases) {
            Meta<TestDefinition> m = this.testCaseToMeta.get(tc);
            Optional<File> fpath = this.getFileFromMeta(m, m.annotation.projectID().toString());
            if (fpath.isPresent())
                this.createTestCaseXML(tc, fpath.get());
        }
        this.tcMap = new HashMap<>();

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

    /**
     * Run processRequirement on Requirements annotated at the class level.
     *
     * Since these are annotated at the class level they must have the projectID
     *
     * @return List of ReqType or null
     */
    private List<ReqType> processAllRequirements() {
        List<ReqType> reqList = this.methToProjectReq.entrySet().stream()
                .flatMap(es -> es.getValue().entrySet().stream()
                        .map(val -> {
                            Meta<Requirement> meta = val.getValue();
                            ReqType req = this.createReqTypeFromRequirement(meta.annotation);
                            return this.processRequirement(meta, req);
                        })
                        .collect(Collectors.toList()).stream())
                .collect(Collectors.toList());
        if(reqList.stream().anyMatch(rt -> rt == null))
            return null;
        return reqList;
    }

    private void createTestCaseXML(Testcase tc,
                                   File path) {
        this.logger.info(String.format("Generating XML description in %s", path.toString()));
        IFileHelper.makeDirs(path.toPath());
        IJAXBHelper.marshaller(tc, path,
                this.jaxb.getXSDFromResource(Testcase.class));
    }

    private Optional<String> setTestcaseProjectID() {
        String projectID = this.config.getProjectID();
        if (this.tcMap.get(projectID).isEmpty()) {
            this.logger.info(String.format("No testcases for %s to import", projectID));
            return Optional.empty();
        }
        this.testcases.setProjectId(projectID);
        this.testcases.setUserId(this.config.getAuthor());
        this.testcases.getTestcase().addAll(this.tcMap.get(projectID));
        return Optional.of(projectID);
    }

    /**
     * Generates the xml file that will be sent via a REST call to the XUnit Importer
     */
    private Map<Testcase, Update> testcasesImporterRequest() {
        Map<Testcase, Update> updatedTests = new HashMap<>();
        if (this.tcMap.isEmpty()) {
            this.logger.info("No more testcases to import ...");
            return updatedTests;
        }

        // TODO: Need to listen to the CI message bus.  Spawn this in a new thread since we need to start it first
        // Look at Java 8's CompleteableFuture

        if (!this.setTestcaseProjectID().isPresent())
            return updatedTests;

        // Find all the testcases that have both <create> and <update>.  Save them in updatedTests
        this.testcases.getTestcase().forEach(
                tc -> {
                    Create create = tc.getCreate();
                    Update update = tc.getUpdate();
                    // If both are non-null delete the Update otherwise it's invalid to the schema
                    if (create != null && update != null) {
                        Update upd = new Update();
                        upd.setId(tc.getUpdate().getId());
                        updatedTests.put(tc, upd);
                    }
                }
        );

        // If it has both <create> and <update> we need to delete the update
        List<Testcase> tests = this.clearTestcaseWithBothCreateAndUpdate();
        this.testcases.getTestcase().clear();
        this.testcases.getTestcase().addAll(tests);

        if (this.polarizeConfig.get("do.xunit").equals("no"))
            return updatedTests;
        CloseableHttpResponse resp = this.sendImporterRequest();
        return updatedTests;
    }

    private List<Testcase> clearTestcaseWithBothCreateAndUpdate() {
        return this.testcases.getTestcase().stream()
                .map(tc -> {
                    Create create = tc.getCreate();
                    Update update = tc.getUpdate();
                    // If both are non-null delete the Update otherwise it's invalid to the schema
                    if (create != null && update != null) {
                        tc.setUpdate(null);
                    }
                    // TODO: Add a <response-property id=>
                    return tc;
                })
                .collect(Collectors.toList());
    }

    private CloseableHttpResponse sendImporterRequest() {
        String url = this.polarizeConfig.get("polarion.url") + this.polarizeConfig.get("importer.testcase.endpoint");
        String testcaseXml = this.polarizeConfig.get("importer.testcases.file");
        String user = this.polarizeConfig.get("kerb.user");
        String pw = this.polarizeConfig.get("kerb.pass");
        return ImporterRequest.request(this.testcases, Testcases.class, url, testcaseXml, user, pw);
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
            TestDefinition ann = m.annotation;
            String meth = m.qualifiedName;
            String project = ann.projectID().toString();

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

    private List<? extends Element>
    getAnnotations(RoundEnvironment env,
                   Class<? extends Annotation> c,
                   Set<ElementKind> allowed,
                   String errMsg) {
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
     * Returns possible file location to the XML description file based on a Meta type and a project
     *
     * Uses the information from the meta type and project to know where to find XML description file
     *
     * @param meta
     * @param project
     * @param <T>
     * @return
     */
    private <T> Optional<File> getFileFromMeta(Meta<T> meta, String project) {
        Path path = FileHelper.makeXmlPath(this.tcPath, meta, project);
        File xmlDesc = path.toFile();
        if (!xmlDesc.exists())
            return Optional.empty();
        return Optional.of(xmlDesc);
    }

    /**
     *  Unmarshalls an Optional of type T from the given Meta object
     *
     * From the data contained in the Meta object, function looks for the XML description file and unmarshalls it to
     * the class given by class t.
     *
     * @param meta
     * @param t class type
     * @param <T> class to unmarshall to
     * @return
     */
    private <T> Optional<T> getTypeFromMeta(Meta<TestDefinition> meta, Class<T> t) {
        //TODO: Check for XML Desc file for TestDefinition
        TestDefinition def = meta.annotation;
        Path path = FileHelper.makeXmlPath(this.tcPath, meta, def.projectID().toString());
        File xmlDesc = path.toFile();
        if (!xmlDesc.exists())
            return Optional.empty();

        this.logger.info("Description file exists: " + xmlDesc.toString());
        Optional<T> witem;
        witem = IJAXBHelper.unmarshaller(t, xmlDesc, this.jaxb.getXSDFromResource(t));
        if (!witem.isPresent())
            return Optional.empty();
        return witem;
    }

    /**
     * Unmarshalls Testcase from XML pointed at in meta, and checks for Update
     *
     * @param meta
     * @return
     */
    private Optional<String> getPolarionIDFromXML(Meta<TestDefinition> meta) {
        Optional<Testcase> tc = this.getTypeFromMeta(meta, Testcase.class);

        if (!tc.isPresent()) {
            this.logger.info("Unmarshalling failed.  No Testcase present...");
            return Optional.empty();
        }
        else if (tc.get().getUpdate() == null) {
            this.logger.info("No <update> element, therefore no ID");
            return Optional.empty();
        }
        else if (tc.get().getUpdate().getId().equals("")) {
            this.logger.info("<update> element exists, but is empty string");
            return Optional.empty();
        }
        Testcase tcase = tc.get();
        this.logger.info("Polarion ID for testcase " + tcase.getTitle() + " is " + tcase.getUpdate().getId());
        return Optional.of(tcase.getUpdate().getId());
    }

    private Optional<String> getPolarionIDFromTestcase(Meta<TestDefinition> meta) {
        TestDefinition def = meta.annotation;
        String id = def.testCaseID();
        if (id.equals(""))
            return Optional.empty();
        return Optional.of(id);
    }

    private Testcase
    initImporterTestcase(Meta<TestDefinition> meta) {
        Testcase tc = new Testcase();
        TestDefinition def = meta.annotation;
        tc.setAutomation("true");
        tc.setImportance(def.importance().stringify());
        tc.setLevel(def.level().stringify());
        tc.setPosneg(def.posneg().stringify());

        if (!def.automation().stringify().equals(""))
            tc.setAutomation(def.automation().stringify());
        if (!def.script().equals(""))
            tc.setAutomationScript(def.script());
        if (!def.component().equals(""))
            tc.setComponent(def.component());
        if (!def.assignee().equals(""))
            tc.setAssignee(def.assignee());
        if (!def.initialEstimate().equals(""))
            tc.setInitialEstimate(def.initialEstimate());
        if (!def.setup().equals(""))
            tc.setSetup(def.setup());
        if (!def.teardown().equals(""))
            tc.setTeardown(def.teardown());
        if (!def.subcomponent().equals(""))
            tc.setSubcomponent(def.subcomponent());
        if (!def.tags().equals(""))
            tc.setTags(def.tags());

        if (def.title().equals(""))
            tc.setTitle(meta.qualifiedName);
        else
            tc.setTitle(def.title());

        if (def.description().equals(""))
            tc.setDescription(this.methNameToTestNGDescription.get(meta.qualifiedName));
        else
            tc.setDescription(def.description());

        TestType tType = def.testtype();
        this.setTestType(tType, tc);
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
        this.initRequirementsFromTestDef(meta, tc);

        // Check to see if there is an existing XML description file with Polarion ID
        Optional<File> xml = this.getFileFromMeta(meta, meta.annotation.projectID().toString());
        if (!xml.isPresent()) {
            Path path = FileHelper.makeXmlPath(this.tcPath, meta, meta.annotation.projectID().toString());
            File xmlDesc = path.toFile();
            this.createTestCaseXML(tc, xmlDesc);
        }

        Optional<String> maybePolarionID = this.getPolarionIDFromTestcase(meta);
        Optional<String> maybeIDXml = this.getPolarionIDFromXML(meta);
        Boolean idExists = maybePolarionID.isPresent();
        Boolean xmlIdExists = maybeIDXml.isPresent();

        Boolean importRequest = false;
        // xmlIdExists  | idExists       | action            | How does this happen?
        // =============|================|===================|=============================================
        // false        | false          | create            | no <update> or "" and no id in annotation
        // false        | true           | create and update | no <update> or "" but id is in annotation
        // true         | false          | update            | <update> is valid, but not in annotation
        // true         | true           | update            | <update> is valid, and in annotation
        if (!xmlIdExists) {
            Create create = new Create();
            create.setAuthorId(def.author());
            tc.setCreate(create);
            importRequest = true;
        }
        if (idExists) {
            Update update = new Update();
            update.setId(maybePolarionID.get());
            tc.setUpdate(update);
            if (def.update())
                importRequest = true;
        }

        if (importRequest) {
            String projId = def.projectID().toString();
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

    private void setTestType(TestType tType, Testcase tc) {
        String sub1 = tType.subtype1().toString();
        String sub2 = tType.subtype2().toString();
        // FIXME: Ughhh I wish Java had something like clojure's extend protocol.  Great example of expression problem
        // here.  Functional, Nonfunctional, and Structural should have implemented a common interface.
        switch(tType.testtype()) {
            case FUNCTIONAL:
                Functional func = new Functional();
                func.setSubtype1(sub1);
                func.setSubtype2(sub2);
                tc.setFunctional(func);
                break;
            case NONFUNCTIONAL:
                Nonfunctional nf = new Nonfunctional();
                nf.setSubtype1(sub1);
                nf.setSubtype2(sub2);
                tc.setNonfunctional(nf);
                break;
            case STRUCTURAL:
                Structural struc = new Structural();
                struc.setSubtype1(sub1);
                struc.setSubtype2(sub2);
                tc.setStructural(struc);
                break;
            default:
                throw new TestDefinitionAnnotationError();
        }
    }

    /**
     * Gets @Requirements from within a @TestDefinition and processes them
     *
     * @param meta a Meta type representing metadata of a method
     * @param tc
     */
    private void initRequirementsFromTestDef(Meta<TestDefinition> meta, Testcase tc) {
        TestDefinition pol = meta.annotation;
        Requirement[] reqs = pol.reqs();
        // If reqs is empty look at the class annotated requirements contained in methToProjectReq
        if (reqs.length == 0) {
            String pkgClassname = String.format("%s.%s", meta.packName, meta.className);
            String project = pol.projectID().toString();
            if (this.methToProjectReq.containsKey(pkgClassname)) {
                Meta<Requirement> r = this.methToProjectReq.get(pkgClassname).get(project);
                reqs = new Requirement[1];
                reqs[0] = r.annotation;
            }
            else {
                String err = String.format("\nThere is no requirement for %s.", tc.getTitle());
                String err2 = "\nEither the class must be annotated with @Requirement, or the " +
                        "@TestDefinition(reqs={@Requirement(...)}) must be filled in";
                this.logger.error(err + err2);
                throw new RequirementAnnotationException();
            }
        }
        // FIXME: The TestCase importer does not yet have a linked work item section.  So we can't add Requirements
        //Testcase.Requirements treq = new Testcase.Requirements();
        //List<ReqType> r = treq.getRequirement();
        for(Requirement e: reqs) {
            Meta<Requirement> m = new Meta<>(meta);
            m.annotation = e;
            String projID = pol.projectID().toString();
            ProjectVals proj = ProjectVals.fromValue(projID);
            ReqType req = this.processRequirement(m, proj);
            //r.add(req);
        }
        //tc.setRequirements(treq);
    }

    /**
     * Generates XML description files for Requirements
     * <p/>
     * Given the Requirement annotation data, do the following:
     * <p/>
     * <ul>
     *   <li>Check if requirements.xml.path/class/methodName.xml exists</li>
     *   <ul>
     *     <li>Verify the XML has TestDefinition ID</li>
     *     <ul>
     *       <li>If the ID doesn't exist, look in the annotation.</li>
     *       <li>If the annotation doesn't have it, issue a WorkItem importer request</li>
     *     </ul>
     *   </ul>
     *   <ul>
     *     <li>Verify that the TestDefinition ID matches the method</li>
     *   </ul>
     *   <li>If xml description file does not exist:</li>
     *   <ul>
     *     <li>Generate XML description and request for WorkItem importer</li>
     *     <li>Wait for return value to get the Requirement ID</li>
     *   </ul>
     * </ul>
     *
     * @param meta Meta of type Requirement that holds data to fully initialize req
     * @param req (possibly) partially initialized ReqType
     * @return fully initialized ReqType object
     */
    private ReqType processRequirement(Meta<Requirement> meta, ReqType req) {
        Requirement r = meta.annotation;
        if (req.getProject().value().equals(""))
            throw new RequirementAnnotationException();

        String projID = req.getProject().value();
        Path path = FileHelper.makeXmlPath(this.reqPath, meta, projID);
        File xmlDesc = path.toFile();
        Optional<WorkItem> wi;

        if (path.toFile().exists()) {
            // If we override, regenerate the XML description file
            if (r.update()) {
                this.createRequirementXML(req, xmlDesc);
            }
            wi = IJAXBHelper.unmarshaller(WorkItem.class, xmlDesc, this.jaxb.getXSDFromResource(WorkItem.class));
            if (!wi.isPresent())
                throw new XMLDescriptonCreationError();

            // Check if the ID is in the xml description file
            WorkItem item = wi.get();
            String id = item.getRequirement().getId();
            if (id.equals("")) {
                //this.testCaseImporterRequest(xmlDesc);
            }
            return wi.get().getRequirement();
        }
        else {
            IFileHelper.makeDirs(path);
            if(r.id().equals("")) {
                this.logger.info("No polarionID...");
                // Check for xmlDesc.  If both the id and xmlDesc are empty strings, then we need to generate XML file
                // based on the Requirement metadata.  If xmlDesc is not an empty string, validate it and unmarshall
                // it to a WorkItem
                if (r.xmlDesc().equals("")) {
                    ReqType generated = this.createRequirementXML(req, xmlDesc);  // generate the desc file
                    //this.testCaseImporterRequest(xmlDesc);
                    return generated;
                }
                else {
                    String finalPath = r.xmlDesc();
                    Path xmlpath = IFileHelper.makeRequirementXmlPath(finalPath, "");
                    File desc = xmlpath.toFile();
                    // FIXME: If the desc doesn't exist, should this be an error?
                    if (!desc.exists()) {
                        this.logger.info("xmlDesc was given but doesn't exist.  Generating one...");
                        IFileHelper.makeDirs(xmlpath);
                        ReqType generated = this.createRequirementXML(req, desc);
                        //this.testCaseImporterRequest(desc);
                        return generated;
                    }
                    else {
                        this.logger.info(String.format("%s exists. Validating...", xmlpath.toString()));
                        URL xsdv = this.jaxb.getXSDFromResource(WorkItem.class);
                        wi = IJAXBHelper.unmarshaller(WorkItem.class, desc, xsdv);
                        if (wi.isPresent())
                            return wi.get().getRequirement();
                        else
                            throw new XMLDescriptionError();
                    }
                }
            }
            else {
                this.logger.info("TODO: TestDefinition ID was given. Chech if xmldesc has the same");
                wi = IJAXBHelper.unmarshaller(WorkItem.class, xmlDesc, this.jaxb.getXSDFromResource(WorkItem.class));
                if (wi.isPresent())
                    return wi.get().getRequirement();
                else
                    throw new XMLDescriptionError();
            }
        }
    }

    /**
     * Creates an xml file based on information from a ReqType
     *
     * @param req the ReqType that will be marshalled into XML
     * @param xmlpath path to marshall the xml to
     * @return fully initialized ReqType
     */
    private ReqType createRequirementXML(ReqType req, File xmlpath) {
        this.logger.info(String.format("Generating XML requirement descriptor in %s", xmlpath.toString()));
        WorkItem wi = new WorkItem();
        wi.setRequirement(req);
        wi.setProjectId(req.getProject());
        wi.setType(WiTypes.REQUIREMENT);

        // TODO: Validate that the xmlpath is a valid XML file conforming to the schema
        IJAXBHelper.marshaller(wi, xmlpath, this.jaxb.getXSDFromResource(wi.getClass()));
        return wi.getRequirement();
    }


    /**
     * Examines a Requirement object to obtain its values and generates an XML file
     * <p/>
     * First, it will check to see if id is an empty string.  Next, it will check if the xmlDesc value is also an
     * empty string.  If both are empty, then given the rest of the information from the annotation, it will generate
     * an XML file and place it in:
     * <p/>
     * resources/requirements/{package}/{class}/{methodName}.xml
     *
     * @param m Meta of type Requirement that will be processed
     * @param project eg RHEL_6
     */
    private ReqType processRequirement(Meta<Requirement> m, ProjectVals project) {
        ReqType init = this.createReqTypeFromRequirement(m.annotation);
        init.setProject(project);
        return this.processRequirement(m, init);
    }

    private ReqType createReqTypeFromRequirement(Requirement r) {
        ReqType req = new ReqType();
        req.setAuthor(r.author());
        req.setDescription(r.description());
        req.setId(r.id());
        req.setPriority(r.priority().stringify());
        try {
            req.setProject(ProjectVals.fromValue(r.project().toString()));
        } catch (Exception ex) {
            this.logger.warn("No projectID...will try from @TestDefinition");
        }
        req.setReqtype(r.reqtype().stringify());
        req.setSeverity(r.severity().stringify());
        return req;
    }



    @Override
    public synchronized void init(ProcessingEnvironment env) {
        System.out.println("In init() method");
        super.init(env);
        this.elements = env.getElementUtils();
        this.msgr = env.getMessager();
        this.logger = LoggerFactory.getLogger(TestDefinitionProcessor.class);
        //this.types = env.getTypeUtils();
        //this.filer = env.getFiler();

        Map<String, String> config = Configurator.loadConfiguration();
        this.reqPath = config.get("requirements.xml.path");
        this.tcPath = config.get("testcases.xml.path");

        this.methNameToTestNGDescription = new HashMap<>();
        this.methToRequirement = new HashMap<>();
        this.methToProjectReq = new HashMap<>();
        this.testCaseToMeta = new HashMap<>();
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
