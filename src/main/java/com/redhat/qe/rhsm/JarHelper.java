package com.redhat.qe.rhsm;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;
import static java.nio.file.StandardOpenOption.*;


import com.google.gson.GsonBuilder;
import joptsimple.OptionParser;
import joptsimple.OptionSet;

import com.google.gson.Gson;

/**
 * Created by stoner on 3/9/16.
 *
 * Takes a jar file from the classpath,
 */
public class JarHelper {
    List<URL> jarPaths;
    String paths;

    public JarHelper(String paths) {
        this.jarPaths = JarHelper.convertToUrl(paths);
        this.paths = this.jarPaths.stream()
                .map( u -> u.getFile() )
                .reduce("", (i, c) -> c + "," + i);
        System.out.println("In constructor: " + this.paths);
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


    public URLClassLoader makeLoader () {
        //List<String> jars = new ArrayList<>(Arrays.asList(jarNames));
        List<URL> urls = this.jarPaths;
        return new URLClassLoader(urls.toArray(new URL[urls.size()]));
    }


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
     * Takes a possibly comma separated string of paths and converts it to a List of URLs
     *
     * @param paths
     * @return
     */
    public static List<URL> convertToUrl(String paths) {
        ArrayList<String> jars = new ArrayList<>(Arrays.asList(paths.split(",")));
        List<URL> jarUrls = jars.stream()
                .map(j -> {
                    URL url = null;
                    System.out.println(j);
                    try {
                        url = new URL(j);
                    } catch (MalformedURLException e) {
                        e.printStackTrace();
                    }
                    return url;
                })
                .collect(Collectors.toList());
        return jarUrls;
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
        parser.accepts("output");

        OptionSet opts = parser.parse(args);
        String jarPathsOpt = (String) opts.valueOf("jars");
        String packName = (String) opts.valueOf("packages");
        String output = (String) opts.valueOf("output");
        if (output == null) {
            output = System.getProperty("user.dir") + "/groups-to-methods.json";
        }

        JarHelper jh = new JarHelper(jarPathsOpt);
        try {
            for(String s: jh.paths.split(",")) {
                List<String> classes = JarHelper.loadJar(s, packName);
                classes.forEach(System.out::println);
                Reflector refl = jh.loadClasses(classes);
                //refl.showMap();

                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                String json = gson.toJson(refl.testsToClasses);

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
                    bw.write(json);
                    bw.close();
                } catch (IOException ex) {
                    System.err.println(ex);
                }
                System.out.println(json);

            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
