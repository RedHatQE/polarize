package com.github.redhatqe.byzantine.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Created by stoner on 5/22/17.
 */
public class Tester {
    public static final Logger logger = LogManager.getLogger("com.github.redhatqe.byzantine.out");
    public static void main(String[] args) {
        String envDir = System.getProperty("POLARIZE_CONFIG");
        if (envDir == null)
            envDir = "xml-configuration.xml";
        logger.info("W00t!! got {}", envDir);
        throw new Error("Failed");
    }
}
