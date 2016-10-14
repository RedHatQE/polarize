package com.github.redhatqe.polarize;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

/**
 * TODO: The properties are starting to get a little more complicated.  Let's use jackson or snake to make a YAML
 * based configuration file.
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
        Map<String, String> config = null; // = new HashMap<>();

        // Preference is for ~/.polarize/polarize.properties file
        try {
            String homeDir = System.getProperty("user.home");
            BufferedReader rdr;
            rdr = Files.newBufferedReader(FileSystems.getDefault().getPath(homeDir + "/.polarize/polarize.properties"));
            props.load(rdr);
            Set<String> keyset = props.stringPropertyNames();
            config = keyset.stream().collect(Collectors.toMap(k -> k, props::getProperty));
        } catch (IOException e) {
            logger.info("Could not load polarize.properties.  Looking for defines...");
        }

        if (config == null) {
            config = new HashMap<>();
        }

        // FIXME: There are a lot of properties now.  Require a config file, but allow env vars to override
        String xmlReqPath = System.getProperty("requirements.xml.path");
        if (xmlReqPath != null)
            config.put("requirements.xml.path", xmlReqPath);

        String xmlTCPath = System.getProperty("testcases.xml.path");
        if (xmlTCPath != null)
            config.put("testcases.xml.path", xmlTCPath);

        return config;
    }
}
