package com.redhat.qe.rhsm;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import joptsimple.OptionParser;
import joptsimple.OptionSet;

/**
 * Created by stoner on 3/9/16.
 *
 * Takes a jar file from the classpath,
 */
public class JarHelper {
    ArrayList<URL> urls;

    public JarHelper() {
        this.urls = new ArrayList<>();
    }


    /**
     * Given the path to a jar file, get all the .class files
     * @param jarPath
     */
    public static List<String> loadJar(String jarPath, String pkg) throws IOException {
       try(ZipFile zf = new ZipFile(jarPath)) {
           return zf.stream()
                   .filter(e -> !e.isDirectory() && e.getName().endsWith(".class") && !e.getName().contains("$"))
                   .map(e -> {
                       String className = e.getName().replace('/', '.');
                       String test = className.substring(0, className.length() - ".class".length());
                       return test;
                   })
                   .filter(e -> e.contains(pkg))
                   .collect(Collectors.toList());
       }
    }


    public void loader (String className) {
        
    }


    /**
     * Takes the jars (which must also be on the classpath) and the names of packages
     * eg java -cp sm-0.0.1-SNAPSHOT.jar --jar sm-0.0.1-SNAPSHOT  --packages "rhsm.cli.tests,rhsm.gui.tests"
     * @param args
     */
    public static void main(String[] args) {
        OptionParser parser = new OptionParser();
        parser.accepts("jars").withRequiredArg();
        parser.accepts("packages").withRequiredArg();

        OptionSet opts = parser.parse(args);
        String jarName = (String) opts.valueOf("jars");
        String packName = (String) opts.valueOf("packages");

        try {
            List<String> classes = JarHelper.loadJar(jarName, packName);
            classes.forEach(System.out::println);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
