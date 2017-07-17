package com.github.redhatqe.polarize.messagebus;

import com.github.redhatqe.byzantine.configuration.Serializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.util.Optional;

/**
 *
 */
public interface ICIBus {
    Logger logger = LogManager.getLogger("byzantine-" + ICIBus.class.getName());

    static String getDefaultConfigPath() {
        String home = System.getProperty("user.home");
        return FileSystems.getDefault().getPath(home, "/.polarize/busconfig.yaml").toString();
    }

    static public <T> Optional<T> getConfigFromPath(Class<T> cfg, String path) {
        T config = null;
        try {
            if(path.endsWith(".json"))
                config = Serializer.fromJson(cfg, new File(path));
            else if (path.endsWith(".yaml"))
                config = Serializer.fromYaml(cfg, new File(path));
            else
                logger.error("Unknown configuration file type");
        } catch (IOException e) {
            logger.error(e.getMessage());
        }
        return Optional.ofNullable(config);
    }
}
