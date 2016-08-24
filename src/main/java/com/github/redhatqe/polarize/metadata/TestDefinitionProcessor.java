package com.github.redhatqe.polarize.metadata;

import com.github.redhatqe.polarize.*;
import com.github.redhatqe.polarize.exceptions.TestDefinitionAnnotationError;
import com.github.redhatqe.polarize.exceptions.XMLDescriptionError;
import com.github.redhatqe.polarize.importer.testcase.Create;
import com.github.redhatqe.polarize.importer.testcase.Functional;
import com.github.redhatqe.polarize.importer.testcase.Nonfunctional;
import com.github.redhatqe.polarize.importer.testcase.Structural;
import com.github.redhatqe.polarize.schema.*;

import com.github.redhatqe.polarize.exceptions.RequirementAnnotationException;
import com.github.redhatqe.polarize.exceptions.XMLDescriptonCreationError;
import org.testng.annotations.Test;

import javax.annotation.Nonnull;
import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;
import javax.xml.bind.*;
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
                            Meta<TestDefinition>>> methToProjectPol;
    private Map<String, String> methNameToTestNGDescription;
    public JAXBHelper jaxb = new JAXBHelper();

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

        // Get all the @Requirement annotated on a class that are not repeated. We will use the information here to
        // generate the XML for requirements.  If a @TestDefinition annotated test method has an empty reqs, we will look in
        // methToRequirements for the associated Requirement.
        allowed = new TreeSet<>();
        allowed.add(ElementKind.CLASS);
        allowed.add(ElementKind.METHOD);
        err = "Can only annotate classes or methods with @Requirement";
        List<? extends Element> reqAnns = this.getAnnotations(roundEnvironment, Requirement.class, allowed, err);
        requirements.addAll(this.makeMeta(reqAnns, Requirement.class));
        this.methToProjectReq.putAll(this.createMethodToMetaRequirementMap(requirements));

        // Get all the @TestDefinition annotations which were annotated on an element only once.
        // Make a list of Meta types that store the fully qualified name of every @TestDefinition annotated
        // method.  We will use this to create a map of qualified_name => TestDefinition Annotation
        allowed = new TreeSet<>();
        allowed.add(ElementKind.METHOD);
        err = "Can only annotate methods with @TestDefinition";
        List<? extends Element> polAnns = this.getAnnotations(roundEnvironment, TestDefinition.class, allowed, err);
        List<Meta<TestDefinition>> metas = this.makeMeta(polAnns, TestDefinition.class);

        // Get all the @TestDefinition annotations which have been repeated on a single element.
        List<? extends Element> pols = this.getAnnotations(roundEnvironment, TestDefinitions.class, allowed, err);
        metas.addAll(this.makeMetaFromPolarions(pols, TestDefinitions.class));

        this.methToProjectPol = this.createMethodToMetaPolarionMap(metas);

        // Get all the @Test annotations in order to get the description
        Map<String, String> methToDescription = this.getTestAnnotations(roundEnvironment);
        this.methNameToTestNGDescription.putAll(methToDescription);

        List<ReqType> reqList = this.processAllRequirements();
        if (reqList == null)
            return false;
        //List<Testcase> tests = this.processAllTestCase();
        List<com.github.redhatqe.polarize.importer.testcase.Testcase> tests = this.processAllTC();

        //this.createWorkItems(tests, ProjectVals.RED_HAT_ENTERPRISE_LINUX_7);
        return true;
    }

    /**
     * From the mapping of qualifiedName -> Annotation stored in methToProjectPol, process each item
     *
     * The primary role of this function is to run processTestCase method given the objects in methToProjectPol
     *
     * @return List of Testcase
     */
    private List<Testcase> processAllTestCase() {
        // We now have the mapping from qualified name to annotation.  So, process each TestDefinition object
        return this.methToProjectPol.entrySet().stream()
                .flatMap(es -> {
                    String qualifiedName = es.getKey();
                    @Nonnull String desc = methNameToTestNGDescription.get(qualifiedName);
                    return es.getValue().entrySet().stream()
                            .map(val -> {
                                Meta<TestDefinition> meta = val.getValue();
                                return this.processTestCase(meta, desc);
                            })
                            .collect(Collectors.toList()).stream();
                })
                .collect(Collectors.toList());
    }

    /**
     *
     * @return
     */
    private List<com.github.redhatqe.polarize.importer.testcase.Testcase> processAllTC() {
        // We now have the mapping from qualified name to annotation.  So, process each TestDefinition object
        return this.methToProjectPol.entrySet().stream()
                .flatMap(es -> {
                    String qualifiedName = es.getKey();
                    @Nonnull String desc = methNameToTestNGDescription.get(qualifiedName);
                    return es.getValue().entrySet().stream()
                            .map(val -> {
                                Meta<TestDefinition> meta = val.getValue();
                                return this.processTC(meta, desc);
                            })
                            .collect(Collectors.toList()).stream();
                })
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
        for(ReqType rt: reqList) {
            if (rt == null) {
                reqList = null;
            }
        }
        return reqList;
    }

    /**
     * Creates an XML description file for a testcase
     *
     * @param tc the Testcase object to serialize to XML
     * @param path path to generate the description file
     */
    private void createTestCaseXML(Testcase tc, File path) {
        this.logger.info(String.format("Generating XML description in %s", path.toString()));
        WorkItem wi = new WorkItem();
        wi.setTestcase(tc);
        wi.setProjectId(tc.getProject());
        wi.setType(WiTypes.TEST_CASE);
        IJAXBHelper.marshaller(wi, path, this.jaxb.getXSDFromResource(wi.getClass()));
    }

    /**
     * Generates an XML description file of a workitems type
     *
     * @param tests a list of all Testcase objects to serialize
     * @param proj which project to create
     */
    private void createWorkItems(List<Testcase> tests, ProjectVals proj) {
        // Convert the TestcaseType objects to XML
        TestCaseMetadata tcmd = new TestCaseMetadata();
        tcmd.setProjectId(proj);
        tcmd.setDryRun(true);

        TestCaseMetadata.Workitems wis = new TestCaseMetadata.Workitems();
        List<Testcase> tcs = wis.getTestcase();
        tcs.addAll(tests);
        tcmd.setWorkitems(wis);

        try {
            JAXBContext jaxbc = JAXBContext.newInstance(TestCaseMetadata.class);
            Marshaller marshaller = jaxbc.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
            marshaller.marshal(tcmd, new File("/tmp/testing.xml"));
            marshaller.marshal(tcmd, System.out);
        } catch (PropertyException pe){
            pe.printStackTrace();
        } catch (JAXBException e) {
            e.printStackTrace();
        }
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

    private <T> Optional<File> getFileFromMeta(Meta<T> meta, String project) {
        Path path = FileHelper.makeXmlPath(this.tcPath, meta, project);
        File xmlDesc = path.toFile();
        if (!xmlDesc.exists())
            return Optional.empty();
        return Optional.of(xmlDesc);
    }

    /**
     *
     * @param meta
     * @return
     */
    private Optional<WorkItem> getWorkItemFromMeta(Meta<TestDefinition> meta) {
        //TODO: Check for XML Desc file for TestDefinition
        TestDefinition def = meta.annotation;
        Path path = FileHelper.makeXmlPath(this.tcPath, meta, def.projectID().toString());
        File xmlDesc = path.toFile();
        if (!xmlDesc.exists())
            return Optional.empty();

        this.logger.info("Description file exists: " + xmlDesc.toString());
        Optional<WorkItem> witem;
        witem = IJAXBHelper.unmarshaller(WorkItem.class, xmlDesc, this.jaxb.getXSDFromResource(WorkItem.class));
        if (!witem.isPresent())
            throw new XMLDescriptonCreationError();
        return witem;
    }

    private Optional<String> getPolarionID(Meta<TestDefinition> meta) {
        Optional<WorkItem> wi = this.getWorkItemFromMeta(meta);

        if (!wi.isPresent() || wi.get().getTestcase().getWorkitemId().equals(""))
            return Optional.empty();
        return Optional.of(wi.get().getTestcase().getWorkitemId());
    }


    /**
     * TODO: Takes a feature file in gherkin style, and generates an XML file
     *
     * @param featureFile
     */
    private void featureToRequirement(String featureFile) {

    }

    /**
     *
     * @param meta
     * @param description
     */
    private com.github.redhatqe.polarize.importer.testcase.Testcase
    processTC(Meta<TestDefinition> meta, String description) {
        com.github.redhatqe.polarize.importer.testcase.Testcase tc =
                new com.github.redhatqe.polarize.importer.testcase.Testcase();

        TestDefinition def = meta.annotation;
        tc.setAutomation("true");

        // Check to see if there is an existing XML description file with Polarion ID
        Optional<String> maybePolarion = this.getPolarionID(meta);
        Boolean createTestCase = false;
        if (!maybePolarion.isPresent()) {
            // This means that there wasn't an ID in the XML description file.  Make a TestCase importer call
            Create create = new Create();
            create.setAuthorId(def.author());
            tc.setCreate(create);
            createTestCase = true;
        }

        tc.setDescription(description);
        tc.setAutomation(def.automation().stringify());
        tc.setAutomationScript(def.script());
        tc.setComponent(def.component());
        tc.setImportance(def.importance().stringify());
        tc.setAssignee(def.assignee());
        tc.setInitialEstimate(def.initialEstimate());
        tc.setLevel(def.level().stringify());
        tc.setPosneg(def.posneg().stringify());
        tc.setSetup(def.setup());
        tc.setTeardown(def.teardown());
        tc.setSubcomponent(def.subcomponent());
        tc.setTitle(def.title());
        tc.setTags(def.tags());

        TestType tType = def.testtype();
        this.setTestType(tType, tc);

        if (createTestCase) {
            JAXBHelper jaxb = new JAXBHelper();
            File path = FileHelper.makeXmlPath(this.tcPath, meta, meta.annotation.projectID().toString()).toFile();
            if (path.exists()) {
                this.logger.warn("Going to overwrite existing xml description");
            }
            IJAXBHelper.marshaller(tc, path,
                    jaxb.getXSDFromResource(com.github.redhatqe.polarize.importer.testcase.Testcase.class));
        }

        return tc;
    }

    private void setTestType(TestType tType, com.github.redhatqe.polarize.importer.testcase.Testcase tc) {
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
     * Examines a TestDefinition object to obtain its values and generates an XML file if needed.
     *
     * @param meta
     * @param description
     * @return
     */
    private Testcase processTestCase(Meta<TestDefinition> meta, String description) {
        TestDefinition pol = meta.annotation;
        Testcase tc = new Testcase();
        tc.setAuthor(pol.author());
        tc.setDescription(description);
        tc.setTitle(meta.qualifiedName);
        this.logger.info("Processing Testcase for " + meta.qualifiedName);

        // For automation, let's always assume we're in draft state
        Testcase.Status status = new Testcase.Status();
        status.setValue("draft");
        tc.setStatus(status);

        Testcase.Caseautomation ca = new Testcase.Caseautomation();
        ca.setValue(AutomationTypes.AUTOMATED);
        tc.setCaseautomation(ca);

        Testcase.Caseimportance ci = new Testcase.Caseimportance();
        ci.setValue(ImpTypes.fromValue(pol.importance().stringify()));
        tc.setCaseimportance(ci);

        Testcase.Caselevel cl = new Testcase.Caselevel();
        cl.setValue(CaseTypes.fromValue(pol.level().stringify()));
        tc.setCaselevel(cl);

        Testcase.Caseposneg cpn = new Testcase.Caseposneg();
        cpn.setValue(PosnegTypes.fromValue(pol.posneg().stringify()));
        tc.setCaseposneg(cpn);

        Testcase.Testtype tt = new Testcase.Testtype();
        tt.setValue(TestTypes.fromValue(pol.testtype().toString()));
        tc.setTesttype(tt);

        tc.setWorkitemId(pol.testCaseID());
        tc.setWorkitemType(WiTypes.TEST_CASE);

        // the setup, teardown and teststeps fields are optional

        tc.setProject(ProjectVals.fromValue(meta.annotation.projectID().toString()));

        Requirement[] reqs = pol.reqs();
        // If reqs is empty look at the class annotated requirements contained in methToProjectReq
        if (reqs.length == 0) {
            String pkgClassname = String.format("%s.%s", meta.packName, meta.className);
            String project = tc.getProject().value();
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
        Testcase.Requirements treq = new Testcase.Requirements();
        List<ReqType> r = treq.getRequirement();
        for(Requirement e: reqs) {
            Meta<Requirement> m = new Meta<>(meta);
            m.annotation = e;
            ProjectVals proj = tc.getProject();
            ReqType req = this.processRequirement(m, proj);
            r.add(req);
        }
        tc.setRequirements(treq);

        //TODO: Check for XML Desc file for TestDefinition
        Path path = FileHelper.makeXmlPath(this.tcPath, meta, tc.getProject().value());
        File xmlDesc = path.toFile();
        if (xmlDesc.exists()) {
            this.logger.info("Description file already exists: " + xmlDesc.toString());
            // TODO: verify the description file has everything needed
            // TODO: validate the xml against the schema.

            // If we override, regenerate the XML description file
            if (pol.override()) {
                this.createTestCaseXML(tc, xmlDesc);
            }

            Optional<WorkItem> witem;
            witem = IJAXBHelper.unmarshaller(WorkItem.class, xmlDesc, this.jaxb.getXSDFromResource(WorkItem.class));
            if (!witem.isPresent())
                throw new XMLDescriptonCreationError();

            // Check if the ID is in the xml description file
            WorkItem item = witem.get();
            String id = item.getTestcase().getWorkitemId();
            if (id.equals("")) {
                this.workItemImporterRequest(xmlDesc);
            }
            tc = witem.get().getTestcase();
        }
        else {
            Path parent = path.getParent();
            Boolean success = parent.toFile().mkdirs();
            this.createTestCaseXML(tc, xmlDesc);
        }

        return tc;
    }

    /**
     * TODO: stub to make a request to the WorkItem importer
     *
     * @param descFile the xml description file to pass to the importer
     */
    private void workItemImporterRequest(File descFile) {
        this.logger.info(String.format("TODO: Need to make a WorkItem importer request for %s", descFile.toString()));
        this.logger.info("TODO: Look for return from WorkItem importer.  Should contain ID");
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

        // If the xml description file exists, verify that it conforms to the schema.
        // If the description file does not exist, make a request to the WorkItem importer
        if (path.toFile().exists()) {
            // TODO: validate the xml against the schema.

            // If we override, regenerate the XML description file
            if (r.override()) {
                this.createRequirementXML(req, xmlDesc);
            }
            wi = IJAXBHelper.unmarshaller(WorkItem.class, xmlDesc, this.jaxb.getXSDFromResource(WorkItem.class));
            if (!wi.isPresent())
                throw new XMLDescriptonCreationError();

            // Check if the ID is in the xml description file
            WorkItem item = wi.get();
            String id = item.getRequirement().getId();
            if (id.equals("")) {
                this.workItemImporterRequest(xmlDesc);
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
                    this.workItemImporterRequest(xmlDesc);
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
                        this.workItemImporterRequest(desc);
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
        req.setReqtype(r.reqtype());
        req.setSeverity(r.severity());
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
        this.reqPath = config.get("reqPath");
        this.tcPath = config.get("tcPath");

        this.methNameToTestNGDescription = new HashMap<>();
        this.methToRequirement = new HashMap<>();
        this.methToProjectReq = new HashMap<>();
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
