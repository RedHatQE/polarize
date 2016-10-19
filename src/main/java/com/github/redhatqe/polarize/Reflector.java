package com.github.redhatqe.polarize;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.redhatqe.polarize.configuration.XMLConfig;
import com.github.redhatqe.polarize.importer.testcase.Testcase;
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
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

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

    public Reflector() {
        config = new XMLConfig(null);
        testsToClasses = new HashMap<>();
        testTypes = new HashSet<>(Arrays.asList("AcceptanceTests", "Tier1Tests", "Tier2Tests", "Tier3Tests"));
        methodToTestDefs = new HashMap<>();
        testDefs = new ArrayList<>();
        mappingFile = FileHelper.loadMapping(new File(config.config.getMapping().getPath()));
        tcPath = config.config.getTestcasesXml().getPath();
    }


    private void showMap() {
        this.testsToClasses.entrySet().forEach((es) -> System.out.println(es.getKey() + "=" + es.getValue()));
    }

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
                                this.logger.error("Length of projects and polarionIds not the same");

                            // TODO: figure out a way to get the parameters.  This code does not get the actual
                            // names of the parameters.  Might need to make Reflector and JarHelper in clojure, and get
                            // the args that way.
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
                                String id;
                                if (polarionIDs.length == 0)
                                    id = "";
                                else
                                    id = polarionIDs[i];
                                metas.add(Meta.create(qual, methName, className, p, project, id, args, ann));
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

    /**
     * Generates the data in the mapping file as needed
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
            logger.info(String.format("%s id is %s", meta.qualifiedName, maybeMapFileID.get()));
            return tc;
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
                Optional<Testcase> maybeTC = meta.getTypeFromMeta(Testcase.class, testCasePath);
                if (maybeTC.isPresent()) {
                    Testcase tcase = maybeTC.get();
                    tcase.setId(maybePolarionID.get());
                    Optional<File> path = meta.getFileFromMeta(testCasePath);
                    if (path.isPresent()) {
                        IJAXBHelper.marshaller(tcase, path.get(), jaxb.getXSDFromResource(Testcase.class));
                    }
                }
            }
        }
        if (xmlIdExists && !idExists) {
            // TODO: The ID exists in the XML file, but not in the annotation.  Set the mapping file with this info
            Map<String, IdParams> projToId = mapFile.getOrDefault(meta.qualifiedName, null);
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
                logger.debug(String.format(msg, meta.qualifiedName, meta.project, maybeIDXml.get()));
                TestDefinitionProcessor.setPolarionIDInMapFile(meta.qualifiedName, meta.project, maybeIDXml.get(),
                        mapFile);
            }
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

    public void processTestDefs() {
        this.testDefs.forEach(td -> Reflector.processTC(td, this.mappingFile, this.testCaseToMeta, this.tcPath,
                this.tcMap));
    }

    public Map<String, Map<String, Meta<TestDefinition>>> makeMethToProjectMeta() {
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


}
