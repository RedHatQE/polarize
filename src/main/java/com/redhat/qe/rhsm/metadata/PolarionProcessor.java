package com.redhat.qe.rhsm.metadata;

import com.redhat.qe.rhsm.schema.*;
import org.testng.annotations.Test;

import javax.annotation.Nonnull;
import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import java.io.File;
import java.lang.annotation.Annotation;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by stoner on 5/16/16.
 */
public class PolarionProcessor extends AbstractProcessor {
    private Types types;
    private Elements elements;
    private Messager msgr;
    private Filer filer;

    /**
     * Contains the fully qualified name of a @Polarion decorated method
     */
    private class Meta<T> {
        public String packName;
        public String className;
        public String methName;
        public String qualifiedName;
        public T annotation;
    }

    /**
     * Recursive function that will get the fully qualified name of a method.
     *
     * Eg packageName.class.methodName
     * @param elem
     * @param accum
     * @return
     */
    String getTopLevel(Element elem, String accum, Meta m) {
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

    private <T> List<Meta<T>> makeMeta(List<? extends Element> elements,
                                       Class<? extends Annotation> ann){
        List<Meta<T>> metas = new ArrayList<>();
        for(Element e : elements) {
            Meta m = new Meta<T>();
            String full = this.getTopLevel(e, "", m);
            System.out.println(String.format("Fully qualified name is %s", full));
            m.qualifiedName = full;
            m.annotation = e.getAnnotation(ann);
            metas.add(m);
        }
        return metas;
    }


    /**
     * The PolarionProcessor actually needs to look for three annotation types:
     * - @Polarion: to get TestCase WorkItem information
     * - @Requirement: to get Requirement WorkItem information
     * - @Test: to get the existing description
     *
     * @param set
     * @param roundEnvironment
     * @return
     */
    @Override
    public boolean process(Set<? extends TypeElement> set, RoundEnvironment roundEnvironment) {
        System.out.println("In process() method");

        // Get all the @Requirement top-level annotations
        // We will use the information here to generate the XML for requirements
        List<? extends Element> reqAnns = this.getRequirementAnnotations(roundEnvironment);

        // Get all the @Polarion annotations
        // Make a list of Meta types that store the fully qualified name of every @Polarion annotated
        // method.  We will use this to create a map of qualified_name => Polarion Annotation
        List<? extends Element> polAnns = this.getPolarionAnnotations(roundEnvironment);
        List<Meta<Polarion>> metas = this.makeMeta(polAnns, Polarion.class);
        Map<String, Meta<Polarion>> methToMeta = this.methodToMeta(metas);
        methToMeta.forEach((qual, ann) -> System.out.println(String.format("%s -> %s", qual, ann.toString())));

        // Get all the @Test annotations in order to get the description
        Map<String, String> methNameToDescription = this.getTestAnnotations(roundEnvironment);

        // We now have the mapping from qualified name to annotation.
        List<TestcaseType> tests = methToMeta.entrySet().stream()
                .map(es -> {
                    String qualifiedName = es.getKey();
                    @Nonnull String desc = methNameToDescription.get(qualifiedName);
                    Meta<Polarion> meta = es.getValue();
                    return this.processTestCase(meta, desc);
                })
                .collect(Collectors.toList());

        // Convert the TestcaseType objects to XML
        // TODO: figure out how to get the project-id
        TestCaseMetadata tcmd = new TestCaseMetadata();
        tcmd.setProjectId(ProjectVals.RED_HAT_ENTERPRISE_LINUX_7);
        tcmd.setDryRun(true);

        TestCaseMetadata.Workitems wis = new TestCaseMetadata.Workitems();
        List<TestcaseType> tcs = wis.getTestcase();
        tcs.addAll(tests);
        tcmd.setWorkitems(wis);

        try {
            JAXBContext jaxbc = JAXBContext.newInstance(TestCaseMetadata.class);
            Marshaller marshaller = jaxbc.createMarshaller();
            marshaller.setProperty( Marshaller.JAXB_FORMATTED_OUTPUT, true);
            marshaller.marshal(tcmd, new File("/tmp/testing.xml"));
            marshaller.marshal(tcmd, System.out);
        } catch (JAXBException e) {
            e.printStackTrace();
        }

        return true;
    }


    private Map<String, ? extends Annotation> methodToAnnotation(List<? extends Element> elements,
                                                                 Class<? extends Annotation> ann) {
        return elements.stream()
                .collect(Collectors.toMap(e -> e.getSimpleName().toString(),
                                          e -> e.getAnnotation(ann)));

    }

    /**
     * Given a sequence of Meta objects, return a Map of the qualified name to the Annotation data
     *
     * @param elements
     * @param <T>
     * @return
     */
    private <T> Map<String, T> methodToAnnotation2(List<Meta<T>> elements) {
        Map<String, T> mToA = new HashMap<>();
        for(Meta<T> m: elements) {
            mToA.put(m.qualifiedName, m.annotation);
        }
        return mToA;
    }

    /**
     * Creates a map of fully qualified names to a Meta type
     *
     * @param metas
     * @param <T>
     * @return
     */
    private <T> Map<String, Meta<T>> methodToMeta(List<Meta<T>> metas) {
        return metas.stream()
                .collect(Collectors.toMap(m -> m.qualifiedName,
                        m -> m));
    }

    private List<? extends Element> getRequirementAnnotations(RoundEnvironment env) {
        String iface = Requirement.class.getSimpleName();
        return env.getElementsAnnotatedWith(Requirement.class)
                .stream()
                .map(ae -> {
                    if (ae.getKind() != ElementKind.METHOD ||
                            ae.getKind() != ElementKind.CLASS) {
                        this.errorMsg(ae, "Can only annotate classes or methods with @%s", iface);
                        return null;
                    }
                    return ae;
                })
                .filter(ae -> ae != null)
                .collect(Collectors.toList());
    }


    /**
     * FIXME: Pass in as argument the Annotation type and a lambda to pass to map
     * @param env
     * @return
     */
    private List<? extends Element> getPolarionAnnotations(RoundEnvironment env) {
        String iface = Polarion.class.getSimpleName();
        List<? extends Element> polAnns = env.getElementsAnnotatedWith(Polarion.class)
                .stream()
                .map(ae -> {
                    if (ae.getKind() != ElementKind.METHOD) {
                        this.errorMsg(ae, "Can only annotate methods with @%s", iface);
                    }
                    return ae;
                })
                .collect(Collectors.toList());
        return polAnns;
    }


    /**
     * Finds methods annotated with @Test and gets the description field.  It returns a map of
     * fully qualified name -> description
     *
     * @param env
     * @return
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


    public File getXMLFileForMethod(Element elem) {
        return null;
    }

    /**
     * Examines a Requirement object to obtain its values and generates an XML file
     *
     * First, it will check to see if id is an empty string.  Next, it will check if the xmlDesc value is also an
     * empty string.  If both are empty, then given the rest of the information from the annotation, it will generate
     * an XML file and place it in:
     *
     * resources/requirements/{package}/{class}/{methodName}.xml
     *
     * @param req
     */
    private void processRequirement(Requirement req) {

    }

    /**
     * Takes a feature file in gherkin style, and generates an XML file
     * @param featureFile
     */
    private void featureToRequirement(String featureFile) {

    }

    /**
     * Examples a Polarion object to obtain its values and generates an XML file if needed.
     * @param meta
     */
    private TestcaseType processTestCase(Meta<Polarion> meta, String description) {
        Polarion pol = meta.annotation;
        TestcaseType tc = new TestcaseType();
        tc.setAuthor(pol.author());
        tc.setDescription(description);
        tc.setTitle(meta.qualifiedName);

        // For automation, let's always assume we're in draft state
        TestcaseType.Status status = new TestcaseType.Status();
        status.setValue("draft");
        tc.setStatus(status);

        TestcaseType.Caseautomation ca = new TestcaseType.Caseautomation();
        ca.setValue(AutomationTypes.AUTOMATED);
        tc.setCaseautomation(ca);

        TestcaseType.Caseimportance ci = new TestcaseType.Caseimportance();
        ci.setValue(ImpTypes.fromValue(pol.caseimportance().toLowerCase()));
        tc.setCaseimportance(ci);

        TestcaseType.Caselevel cl = new TestcaseType.Caselevel();
        cl.setValue(CaseTypes.fromValue(pol.caselevel().toLowerCase()));
        tc.setCaselevel(cl);

        TestcaseType.Caseposneg cpn = new TestcaseType.Caseposneg();
        cpn.setValue(PosnegTypes.fromValue(pol.caseposneg().toLowerCase()));
        tc.setCaseposneg(cpn);

        TestcaseType.Testtype tt = new TestcaseType.Testtype();
        tt.setValue(TestTypes.fromValue(pol.testtype().toLowerCase()));
        tc.setTesttype(tt);

        tc.setWorkitemId(pol.testCaseID());
        tc.setWorkitemType(WiTypes.TEST_CASE);

        /**
         * TODO: add the Requirements
         */
        Requirement[] reqs = pol.reqs();

        return tc;
    }

    @Override
    public synchronized void init(ProcessingEnvironment env) {
        System.out.println("In init() method");
        super.init(env);
        this.types = env.getTypeUtils();
        this.elements = env.getElementUtils();
        this.filer = env.getFiler();
        this.msgr = env.getMessager();
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        Set<String> anns = new LinkedHashSet<>();
        anns.add(Polarion.class.getCanonicalName());
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
}
