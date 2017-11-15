package com.github.redhatqe.polarize.configurator;


import com.github.redhatqe.byzantine.configuration.Serializer;
import com.github.redhatqe.byzantine.configurator.IConfigurator;
import com.github.redhatqe.byzantine.utils.Tuple;
import com.github.redhatqe.polarize.configuration.PolarizeConfig;
import com.github.redhatqe.polarize.utils.Environ;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class PolarizeYAML implements IConfigurator<PolarizeConfig> {
    public PolarizeConfig cfg;
    public String configFileName = "polarize-config.yaml";
    public String configFilePath = "";
    private Logger logger = LogManager.getLogger(PolarizeYAML.class.getSimpleName());

    /**
     * The constructor's main purpose is to find and set the file to be used for configuration
     *
     * @param path
     */
    public PolarizeYAML(String path) {
        File defaultPath;
        String homeDir = System.getProperty("user.home");
        if (path != null && !path.equals(""))
            this.configFileName = path;

        // If POLARIZE_CONFIG is set, prefer that path.  Next is a -Dpolarize.configpath setting, and then finally
        // the default
        String envDir = Environ.getVar("POLARIZE_CONFIG")
                .orElseGet(() -> System.getProperty("polarize.configuration", ""));
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
        logger.info("Using configuration file at " + configFile);

        if (!configFile.exists())
            throw new Error("Polarize Config file does not exist!");

        try {
            this.cfg = Serializer.fromYaml(PolarizeConfig.class, configFile);
            this.configFilePath = configFile.toString();
        } catch (IOException e) {
            this.logger.error("Could not load file from %s.  Using default in ~/.polarize/polarize-config.yaml");
        }
    }

    public PolarizeYAML() {
        this(null);
    }


    @Override
    public PolarizeConfig pipe(PolarizeConfig polarizeConfig, List<Tuple<String, String>> list) {
        return cfg;
    }

}
