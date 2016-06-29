package com.redhat.qe.rhsm.metadata;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
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
        String iface = Requirement.class.getSimpleName();
        List<? extends Element> reqAnns = roundEnvironment.getElementsAnnotatedWith(Requirement.class)
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

        List<? extends Element> polAnns = this.getPolarionAnnotations(roundEnvironment);
        Map<String, ? extends Annotation> mToA = this.methodToAnnotation(polAnns, Polarion.class);

        List<Meta<Polarion>> metas = polAnns.stream()
                .map(e -> {
                    Meta m = new Meta<Polarion>();
                    String full = this.getTopLevel(e, "", m);
                    System.out.println(String.format("Fully qualified name is %s", full));
                    m.qualifiedName = full;
                    m.annotation = e.getAnnotation(Polarion.class);
                    return m;
                })
                .collect(Collectors.toList());

        //Map<String, Polarion> mToA2 = this.methodToAnnotation2(metas);

        mToA.forEach((s, e) -> System.out.println(String.format("%s -> %s", s, e.toString())));
        //mToA.forEach((qual, ann) -> System.out.println(String.format("%s -> %s", qual, ann.toString())));
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
     * @param pol
     */
    private void processTestCase(Polarion pol) {

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
