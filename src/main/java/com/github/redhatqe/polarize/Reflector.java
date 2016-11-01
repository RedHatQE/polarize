package com.github.redhatqe.polarize;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.redhatqe.polarize.configuration.XMLConfig;
import com.github.redhatqe.polarize.exceptions.MismatchError;
import com.github.redhatqe.polarize.importer.ImporterRequest;
import com.github.redhatqe.polarize.importer.testcase.Testcase;
import com.github.redhatqe.polarize.importer.testcase.Testcases;
import com.github.redhatqe.polarize.metadata.DefTypes;
import com.github.redhatqe.polarize.metadata.Meta;
import com.github.redhatqe.polarize.metadata.TestDefAdapter;
import com.github.redhatqe.polarize.metadata.TestDefinition;

import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import javax.jms.JMSException;

import static com.github.redhatqe.polarize.metadata.DefTypes.Custom.*;

/**
 * Created by stoner on 3/9/16.
 */
public class Reflector {

    public HashMap<String, List<MetaData>> testsToClasses;
    public List<MetaData> methods;
    public List<Meta<TestDefinition>> testDefs;
    public List<Meta<TestDefAdapter>> testDefAdapters;
    public Map<String,
               Map<String, Meta<TestDefinition>>> methodToTestDefs;
    private Set<String> testTypes;
    private static Logger logger = LoggerFactory.getLogger(Reflector.class);
    public Map<Testcase, Meta<TestDefinition>> testCaseToMeta = new HashMap<>();
    public Map<String,
                Map<String, IdParams>> mappingFile;
    public XMLConfig config;
    public String tcPath;
    private Map<String, List<Testcase>> tcMap = new HashMap<>();
    public Map<String,
               Map<String, Meta<TestDefinition>>> methToProjectDef;
    private Testcases testcases = new Testcases();

    public Reflector() {
        config = new XMLConfig(null);
        testsToClasses = new HashMap<>();
        testTypes = new HashSet<>(Arrays.asList("AcceptanceTests", "Tier1Tests", "Tier2Tests", "Tier3Tests"));
        methodToTestDefs = new HashMap<>();
        testDefs = new ArrayList<>();
        mappingFile = FileHelper.loadMapping(new File(config.config.getMapping().getPath()));
        tcPath = config.config.getTestcasesXml().getPath();
        tcPath = config.getTestcasesXMLPath();
    }


    private void showMap() {
        this.testsToClasses.entrySet().forEach((es) -> System.out.println(es.getKey() + "=" + es.getValue()));
    }

    /**
     *
     * @param c
     * @param <T>
     * @return
     */
    private <T> List<Meta<TestDefinition>> getTestDefMetaData(Class<T> c) {
        Method[] methods = c.getMethods();
        List<Method> meths = new ArrayList<>(Arrays.asList(methods));
        List<Method> filtered = meths.stream()
                        .filter(m -> m.getAnnotation(TestDefinition.class) != null)
                        .collect(Collectors.toList());
        return filtered.stream().flatMap(m -> {
                            TestDefinition ann = m.getAnnotation(TestDefinition.class);
                            String className = c.getName();
                            Package pkg = c.getPackage();
                            String p = pkg.getName();
                            String methName = m.getName();
                            String qual = className + "." + methName;
                            DefTypes.Project[] projects = ann.projectID();
                            String[] polarionIDs = ann.testCaseID();
                            if (polarionIDs.length > 0 && polarionIDs.length != projects.length)
                                logger.error("Length of projects and polarionIds not the same");

                            if (className.contains(".")) {
                                String[] split = className.split("\\.");
                                className = split[split.length - 1];
                            }

                            // TODO: This doesnt get clojure param names. Might need to make Reflector and JarHelper
                            // in clojure, and get the args that way.
                            Parameter[] params = m.getParameters();
                            List<com.github.redhatqe.polarize.importer.testcase.Parameter> args = Arrays.stream(params)
                                    .map(arg -> {
                                        com.github.redhatqe.polarize.importer.testcase.Parameter pm = new
                                                com.github.redhatqe.polarize.importer.testcase.Parameter();
                                        pm.setName(arg.getName());
                                        pm.setScope("local");
                                        return pm;
                                    })
                                    .collect(Collectors.toList());

                            List<Meta<TestDefinition>> metas = new ArrayList<>();
                            for(int i = 0; i < projects.length; i++) {
                                String project = projects[i].toString();
                                String id = "";
                                Boolean dirty = false;
                                try {
                                    id = polarionIDs[i];
                                }
                                catch (ArrayIndexOutOfBoundsException ae) {
                                    dirty = true;
                                }
                                Meta<TestDefinition> meta = Meta.create(qual, methName, className, p, project, id, args,
                                        ann);
                                if (dirty)
                                    meta.dirty = dirty;
                                metas.add(meta);
                            }
                            return metas.stream().map( me -> me);
                        })
                        .filter(meta -> !meta.className.isEmpty() && !meta.methName.isEmpty())
                        .collect(Collectors.toList());
    }

    /**
     * Gets any methods annotated with TestDefinition
     *
     * @param c
     * @param <T>
     * @return
     */
    public <T> List<MetaData> getTestNGMetaData(Class<T> c) {
        Method[] methods = c.getMethods();
        List<Method> meths = new ArrayList<>(Arrays.asList(methods));
        List<MetaData> classMethods =
                meths.stream()
                        .filter(m -> m.getAnnotation(Test.class) != null)
                        .map(m -> {
                            Test ann = m.getAnnotation(Test.class);
                            String desc = ann.description();
                            String className = c.getName();
                            String methName = m.getName();
                            String provider = ann.dataProvider();
                            Boolean isProvider = !provider.isEmpty();
                            Boolean enabled = ann.enabled();
                            //return className + "." + m.getName();
                            return new MetaData(methName, className, desc, enabled, isProvider, provider);
                        })
                        .filter(e -> !e.className.isEmpty() && !e.methodName.isEmpty())
                        .collect(Collectors.toList());
        return classMethods;
    }

    public <T> void getAnnotations(Class<T> c) {
        List<MetaData> classMethods = this.getTestNGMetaData(c);
        if(this.methods == null) {
            this.methods = classMethods;
        }
        else
            this.methods.addAll(classMethods);

        this.testDefs.addAll(this.getTestDefMetaData(c));

        // Get the groups from the Test annotation, store it in a set
        Annotation ann = c.getAnnotation(Test.class);
        if (ann == null) return;
        String[] groups = ((Test) ann).groups();
        Set<String> groupSet = new TreeSet<>(Arrays.asList(groups));

        // Get only the groups from testTypes
        groupSet = groupSet.stream()
                .filter(testTypes::contains)
                .collect(Collectors.toSet());

        for (String g : groupSet) {
            if (!this.testsToClasses.containsKey(g)) {
                this.testsToClasses.put(g, classMethods);
            } else {
                this.testsToClasses.get(g).addAll(classMethods);
            }
        }
    }



    public void processTestDefs() {
        this.testDefs.forEach(td -> TestDefinitionProcessor.processTC(td, this.mappingFile, this.testCaseToMeta,
                this.tcPath, this.tcMap));
    }

    Map<String, Map<String, Meta<TestDefinition>>> makeMethToProjectMeta() {
        Map<String, Map<String, Meta<TestDefinition>>> methToProjectMeta = new HashMap<>();
        for(Meta<TestDefinition> meta: this.testDefs) {
            String qual = meta.qualifiedName;
            String project = meta.project;
            Map<String, Meta<TestDefinition>> projToMeta = new HashMap<>();
            projToMeta.put(project, meta);
            methToProjectMeta.put(qual, projToMeta);
        }
        return methToProjectMeta;
    }

    static List<Meta<TestDefAdapter>> sortTestDefs(List<Meta<TestDefAdapter>> defs) {
        List<Meta<TestDefAdapter>> adaps = defs.stream().sorted((d1, d2) -> {
            String qual1 = d1.qualifiedName;
            String qual2 = d2.qualifiedName;
            if (qual1 == null || qual2 == null) {
                String class1 = d1.className;
                String class2 = d2.className;
                String meth1 = d1.methName;
                String meth2 = d2.methName;
                qual1 = String.format("%s.%s", class1, meth1);
                qual2 = String.format("%s.%s", class2, meth2);
            }
            return qual1.compareTo(qual2);
        }).collect(Collectors.toList());
        return adaps;
    }

    Optional<ObjectNode> testcasesImporterRequest() {
        Optional<ObjectNode> maybeNode = Optional.empty();
        if (this.tcMap.isEmpty()) {
            logger.info("No more testcases to import ...");
            return maybeNode;
        }

        String sName = this.config.testcase.getSelector().getName();
        String sValue = this.config.testcase.getSelector().getVal();
        String selector = String.format("%s='%s'", sName, sValue);
        String ID = this.config.config.getProject();
        String author = this.config.config.getAuthor();
        File tcXML = new File(this.config.getTCImporterFilePath());
        Optional<String> maybe = TestDefinitionProcessor.initTestcases(sName, sValue, ID, author, tcXML, this.tcMap,
                                                                       this.testcases);
        if (!maybe.isPresent())
            return maybeNode;

        try {
            String user = this.config.polarion.getUser();
            String pass = this.config.polarion.getPassword();
            String url = this.config.polarion.getUrl() + this.config.testcase.getEndpoint().getRoute();
            Consumer<Optional<ObjectNode>> handler =
                    TestDefinitionProcessor.testcaseImportHandler(this.tcPath, ID, this.testcases);
            if (this.config.testcase.isEnabled())
                maybeNode = ImporterRequest.sendImportRequest(url, user, pass, tcXML, selector, handler);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        } catch (JMSException e) {
            e.printStackTrace();
        }
        return maybeNode;
    }
}
