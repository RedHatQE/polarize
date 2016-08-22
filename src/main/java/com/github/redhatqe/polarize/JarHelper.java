package com.github.redhatqe.polarize;

import java.io.*;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileAlreadyExistsException;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.GsonBuilder;
import joptsimple.OptionParser;
import joptsimple.OptionSet;

import com.google.gson.Gson;

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
     * eg java -cp sm-0.0.1-SNAPSHOT.jar --jar sm-0.0.1-SNAPSHOT  --packages "polarize.cli.tests,polarize.gui.tests"
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
            List<String> classes = new ArrayList<>();
            for(String s: jh.paths.split(",")) {
                for(String pn: packName.split(",")){
                    classes.addAll(IJarHelper.loadJar(s, pn));
                }
                classes.forEach(System.out::println);
                Reflector refl = jh.loadClasses(classes);
                //refl.showMap();

                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                //String json = gson.toJson(refl.testsToClasses);
                String json = gson.toJson(refl.methods);

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
