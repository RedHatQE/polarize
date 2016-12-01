package com.github.redhatqe.polarize;

import java.io.*;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileAlreadyExistsException;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.redhatqe.polarize.metadata.Meta;
import com.github.redhatqe.polarize.metadata.TestDefAdapter;
import com.github.redhatqe.polarize.metadata.TestDefinition;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import joptsimple.OptionParser;
import joptsimple.OptionSet;

import com.google.gson.Gson;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Created by stoner on 3/9/16.
 *
 * Takes a jar file from the classpath,
 */
public class JarHelper implements IJarHelper {
    List<URL> jarPaths;
    String paths;

    public JarHelper(String paths) {
        this.jarPaths = IJarHelper.convertToUrl(paths);
        this.paths = this.jarPaths.stream()
                .map( u -> u.getFile() )
                .reduce("", (i, c) -> c + "," + i);
        System.out.println("In constructor: " + this.paths);
    }


    @Override
    public URLClassLoader makeLoader() {
        List<URL> urls = this.jarPaths;
        return new URLClassLoader(urls.toArray(new URL[urls.size()]));
    }


    @Override
    public Reflector loadClasses(List<String> classes) {
        URLClassLoader ucl = this.makeLoader();
        Reflector refl = new Reflector();
        for(String s: classes) {
            try {
                Class<?> cls = ucl.loadClass(s);
                refl.getAnnotations(cls);
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
        return refl;
    }


    /**
     * Takes the jars (which must also be on the classpath) and the names of packages
     * eg java -cp sm-0.0.1-SNAPSHOT.jar --jar file:///path/to/sm-0.0.1-SNAPSHOT-standalone.jar \
     * --packages "polarize.cli.tests,polarize.gui.tests"
     * @param args
     */
    public static void main(String[] args) {
        OptionParser parser = new OptionParser();
        parser.accepts("jars").withRequiredArg();
        parser.accepts("packages").withRequiredArg();
        parser.accepts("output");

        OptionSet opts = parser.parse(args);
        String jarPathsOpt = (String) opts.valueOf("jars");
        String packName = (String) opts.valueOf("packages");
        String output = (String) opts.valueOf("output");
        if (output == null) {
            output = System.getProperty("user.dir") + "/groups-to-methods.json";
        }

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        Type metaType = new TypeToken<List<Meta<TestDefAdapter>>>() {}.getType();

        JarHelper jh = new JarHelper(jarPathsOpt);
        try {
            List<String> classes = new ArrayList<>();
            for(String s: jh.paths.split(",")) {
                for(String pn: packName.split(",")){
                    classes.addAll(IJarHelper.getClasses(s, pn));
                }
                classes.forEach(System.out::println);

                Reflector refl = jh.loadClasses(classes);
                refl.methToProjectDef = refl.makeMethToProjectMeta();
                refl.processTestDefs();

                if (refl.methToProjectDef.size() > 0) {
                    File mapPath = new File(refl.config.getMappingPath());
                    Map<String, Map<String, IdParams>> tmap;
                    tmap = TestDefinitionProcessor.printSortedMappingFile(refl.mappingFile);
                    refl.mappingFile = TestDefinitionProcessor.createMappingFile(mapPath, refl.methToProjectDef, tmap);
                }

                refl.testcasesImporterRequest();
                File mapPath = new File(refl.config.getMappingPath());
                TestDefinitionProcessor.writeMapFile(mapPath, refl.mappingFile);

                refl.testDefAdapters = refl.testDefs.stream()
                        .map(m -> {
                            TestDefinition def = m.annotation;
                            TestDefAdapter adap = TestDefAdapter.create(def);
                            Meta<TestDefAdapter> meta = Meta.create(m.qualifiedName, m.methName, m.className,
                                    m.packName, m.project, m.polarionID, m.params, adap);
                            return meta;
                        })
                        .collect(Collectors.toList());
                List<Meta<TestDefAdapter>> sorted = Reflector.sortTestDefs(refl.testDefAdapters);

                String jsonDefs = gson.toJson(sorted, metaType);
                System.out.println(jsonDefs);

                File file = new File(output);
                if (file.exists()) {
                    boolean deleted = file.delete();
                    if (!deleted) {
                        throw new FileAlreadyExistsException("Could not delete old file");
                    }
                } else {
                    file.createNewFile();
                }

                FileWriter fw = new FileWriter(file);
                BufferedWriter bw = new BufferedWriter(fw);

                try {
                    bw.write(jsonDefs);
                    bw.close();
                } catch (IOException ex) {
                    System.err.println(ex.getMessage());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
