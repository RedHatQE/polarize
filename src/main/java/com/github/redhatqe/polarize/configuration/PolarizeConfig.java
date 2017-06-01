package com.github.redhatqe.polarize.configuration;


import com.github.redhatqe.byzantine.config.Serializer;
import com.github.redhatqe.polarize.utils.Environ;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;

public class PolarizeConfig {
    public FullConfig cfg;
    public String configFileName = "polarize-config.yaml";
    private Logger logger = LogManager.getLogger(PolarizeConfig.class.getSimpleName());

    public PolarizeConfig(String path) {
        File defaultPath;
        String homeDir = System.getProperty("user.home");
        this.configFileName = path;

        // If POLARIZE_CONFIG is set, prefer that path
        String envDir = Environ.getVar("POLARIZE_CONFIG").orElse("");
        if (envDir.equals("")) {
            defaultPath = FileSystems.getDefault()
                    .getPath(homeDir + String.format("/.polarize/%s", this.configFileName)).toFile();
        }
        else {
            Path p = Paths.get(envDir);
            this.configFileName = p.getFileName().toString();
            defaultPath = new File(envDir);
        }

        File configFile;
        if (path == null || path.equals("")) {
            configFile = defaultPath;
        }
        else {
            configFile = new File(path);
            this.configFileName = path;
        }
        logger.info("Using config file at " + configFile);

        if (!configFile.exists())
            throw new Error("Config file does not exist!");

        try {
            this.cfg = Serializer.fromYaml(FullConfig.class, configFile);
        } catch (IOException e) {
            this.logger.error("Could not load file from %s.  Using default in ~/.polarize/polarize-config.yaml");
        }
    }

    public PolarizeConfig() {
        this(null);
    }
}
