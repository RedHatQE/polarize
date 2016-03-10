package com.redhat.qe.rhsm;

import java.net.URL;
import java.net.URLClassLoader;
import java.security.cert.PKIXRevocationChecker;
import java.util.ArrayList;
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
        urls = new ArrayList<>();
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
        System.out.println(opts);
    }
}
