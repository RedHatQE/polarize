package com.github.redhatqe.polarize;

import com.github.redhatqe.polarize.exceptions.ConfigurationError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Created by stoner on 8/3/16.
 */
public class Configurator {

    private static Logger logger;

    static {
        logger = LoggerFactory.getLogger(Configurator.class);
    }

    /**
     * Loads configuration data from the following in increasing order of precedence:
     *
     * - resources/polarize.properties
     * - ~/.polarize/polarize.properties
     * - Java -D options
     *
     * Two of the more important properties are requirements.xml.path and testcase.xml.path.  These fields describe
     * where the XML equivalent of the annotations will be stored.  Normally, this will be some path inside of the
     * project that will be scanned for annotations.  This is because polarize will generate the XML descriptions if
     * they dont exist, and it is better to have these xml files under source control.  When the processor needs to
     * generate an XML description based on the annotation data, it will do the following:
     *
     * - If processing @Requirement, look for/generate requirements.xml.path/class/methodName.xml
     * - If processing @TestDefinition, look for/generate testcase.xml.path/class/methodName.xml
     */
    public static Map<String, String> loadConfiguration() {
        Properties props = new Properties();
        Map<String, String> config = new HashMap<>();

        // Preference is for ~/.polarize/polarize.properties file
        try {
            String homeDir = System.getProperty("user.home");
            BufferedReader rdr;
            rdr = Files.newBufferedReader(FileSystems.getDefault().getPath(homeDir + "/.polarize/polarize.properties"));
            props.load(rdr);
            config.put("reqPath", props.getProperty("requirements.xml.path", "/tmp/reqs"));
            config.put("tcPath", props.getProperty("testcases.xml.path", "/tmp/tcs"));
            return config;
        } catch (IOException e) {
            logger.info("Could not load polarize.properties.  Looking for defines...");
        }

        Boolean tryDefault = false;
        String xmlReqPath = System.getProperty("requirements.xml.path");
        if (xmlReqPath != null)
            config.put("reqPath", xmlReqPath);
        else
            tryDefault = true;

        String xmlTCPath = System.getProperty("testcases.xml.path");
        if (xmlTCPath != null)
            config.put("tcPath", xmlTCPath);
        else
            tryDefault = true;

        if (tryDefault) {
            logger.warn("Using default from project polarize.properties");
            InputStream is = Configurator.class.getClassLoader().getResourceAsStream("polarize.properties");
            try {
                props.load(is);
                String reqPath = props.getProperty("requirements.xml.path", "/tmp/reqs");
                String tcPath = props.getProperty("testcases.xml.path", "/tmp/reqs");
                config.put("reqPath", reqPath);
                config.put("tcPath", tcPath);
                return config;
            } catch (IOException e) {
                throw new ConfigurationError();
            }
        }

        return config;
    }
}
