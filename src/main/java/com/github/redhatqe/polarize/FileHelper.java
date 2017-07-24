package com.github.redhatqe.polarize;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.github.redhatqe.byzantine.exceptions.InvalidArgumentType;
import com.github.redhatqe.byzantine.utils.IFileHelper;
import com.github.redhatqe.polarize.metadata.Meta;
import com.github.redhatqe.polarize.metadata.QualifiedName;
import com.github.redhatqe.polarize.metadata.TestDefinition;
import com.github.redhatqe.polarize.utils.StringHelper;


import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;


public class FileHelper implements IFileHelper {

    /**
     * Creates a Path to look up or create an xml description
     *
     * It uses this pattern:
     *
     * base/[project]/requirements|testcases/[class]/[methodName].xml as the path to the File
     *
     * @param base
     * @param meta
     * @return
     */
    public static <T> Path makeXmlPath(String base, Meta<T> meta, String projID) throws InvalidArgumentType {
        String xmlname;
        xmlname = meta.methName;
        String packClass = meta.packName;
        if (xmlname == null || xmlname.equals("")) {
            xmlname = meta.className;
        }
        else
            packClass += "." + meta.className;

        Path basePath = Paths.get(base, projID, packClass);
        String fullPath = Paths.get(basePath.toString(), xmlname + ".xml").toString();
        return Paths.get(fullPath);
    }

    public static <T> Path makeXmlPath(String base, Meta<T> meta) throws InvalidArgumentType {
        String xmlname;
        String className = meta.className;
        xmlname = meta.methName;
        String packClass = meta.packName;
        if (xmlname == null || xmlname.equals("")) {
            xmlname = meta.className;
        }
        else
            packClass += "." + meta.className;

        Path basePath = Paths.get(base, meta.project, packClass);
        String fullPath = Paths.get(basePath.toString(), xmlname + ".xml").toString();
        return Paths.get(fullPath);
    }

    /**
     * Given a qualified name like com.github.redhat.qe.polarize.Foo.barMethod returns package, class and method
     *
     * @param path
     * @return
     */
    public static QualifiedName getClassMethodFromDottedString(String path) throws Exception {
        QualifiedName qual = new QualifiedName();
        String[] dots = path.split("\\.");
        if (dots.length < 3)
            throw new Exception(String.format("%s not a valid dotted name. Must have package.class.methodname", path));

        qual.methName = dots[dots.length - 1];
        qual.className = dots[dots.length - 2];

        String[] pkg = new String[dots.length - 2];
        System.arraycopy(dots, 0, pkg, 0, pkg.length);
        qual.packName = Arrays.stream(pkg).reduce("", (acc, n) -> acc + "." + n).substring(1);
        return qual;
    }

    /**
     * FIXME: this assumes the path is based on the class.method name but ignores if xmlPath is used in annotation
     *
     * Creates an xml path for a test method.  The path generated will take the form:</br>
     * /{base}/{projID}/{qualname}
     * </p>
     * Normally, the base will come from the configuration file, extra will come from the class.methodName, and project will
     * be determined based on some other factor like the project in a Meta object.
     *
     * @param base From the configuration file \<testcases-xml path={}\>
     * @param extra a string representing an extra set of paths concatenated to base
     * @param projID the project
     * @return
     */
    public static Optional<Path> getXmlPath(String base, String extra, String projID) {
        Path path = null;
        try {
            QualifiedName qual = FileHelper.getClassMethodFromDottedString(extra);
            Meta<TestDefinition> meta = new Meta<>();
            meta.project = projID;
            meta.className = qual.className;
            meta.packName = qual.packName;
            meta.methName = qual.methName;
            path = FileHelper.makeXmlPath(base, meta);
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (path == null)
            return Optional.empty();
        else
            return Optional.of(path);
    }

    public static void main(String[] args) {
        try {
            QualifiedName q = FileHelper.getClassMethodFromDottedString("rhsm.cli.tests.Foo.barMathod");
        } catch (Exception e) {
            e.printStackTrace();
        }

        Optional<Path> p = FileHelper.getXmlPath("/home/stoner/Projects/testpolarize/testcases",
                                                 "com.github.redhatqe.rhsm.testpolarize.TestReq", "PLATTP");
        if(p.isPresent()) {
            Path path = p.get();
            System.out.println(path.toString());
        }
    }

    /**
     * From the path pointing to a mapping.json file, create a Map from it
     *
     * The key to the Map is the unique name of a method (usually its fully qualified name), and the value is a Map
     * of the form Project -> IdParams.  This second map is needed because one test method is often used across multiple
     * projects.
     *
     *
     * @param fpath path to a mapping.json file
     * @return an in-memory map of unique methodname -> project -> IdParams
     */
    public static Map<String, Map<String, IdParams>> loadMapping(File fpath) {
        ObjectMapper mapper = new ObjectMapper();

        Map<String, Map<String, IdParams>> mapped = new HashMap<>();
        try {
            if (fpath.exists()) {
                // First key is name, second is project, third is "id" or "parameters"
                JsonNode node = mapper.readTree(fpath);
                node.fields().forEachRemaining(entry -> {
                    String name = entry.getKey();
                    JsonNode projectToInner = entry.getValue();
                    Map<String, IdParams> innerMap = new HashMap<>();
                    projectToInner.fields().forEachRemaining(e -> {
                        String project = e.getKey();
                        JsonNode inner = e.getValue();
                        String id = inner.get("id").toString();

                        IdParams idp = new IdParams();
                        idp.id = StringHelper.removeQuotes(id);
                        JsonNode paramNode = inner.get("parameters");
                        if (paramNode instanceof ArrayNode) {
                            ArrayNode params = (ArrayNode) paramNode;
                            idp.parameters = params.findValuesAsText("parameters");
                            List<String> p = new ArrayList<>();
                            for(int i = 0; i < params.size(); i++) {
                                p.add(StringHelper.removeQuotes(params.get(i).toString()));
                            }
                            idp.parameters = p;
                        }
                        innerMap.put(project, idp);
                    });
                    mapped.put(name, innerMap);
                });
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return mapped;
    }
}
