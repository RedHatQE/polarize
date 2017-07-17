package com.github.redhatqe.polarize.configurator;

import com.github.redhatqe.byzantine.configuration.Serializer;
import com.github.redhatqe.byzantine.configurator.ICLIConfig;
import com.github.redhatqe.byzantine.configurator.IConfigurator;
import com.github.redhatqe.byzantine.exceptions.NoConfigFoundError;
import com.github.redhatqe.byzantine.utils.ArgHelper;
import com.github.redhatqe.byzantine.utils.Tuple;
import com.github.redhatqe.polarize.configuration.Config;
import com.github.redhatqe.polarize.messagebus.ICIBus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Optional;


public class YAMLConfigurator implements IConfigurator<Config> {
    public String path;
    public final Config config;
    public Logger logger = LogManager.getLogger(YAMLConfigurator.class);

    public YAMLConfigurator(String path) throws IOException {
        this.path = path;
        this.config = Serializer.fromYaml(Config.class, new File(this.path));
    }

    public static YAMLConfigurator build(String path) throws IOException {
        return new YAMLConfigurator(path);
    }

    /**
     * Nothing really happens here in the pipe, since there's nothing to modify.  All we do is return what was passed in
     * @param cfg
     * @param args
     * @return
     */
    @Override
    public Config pipe(Config cfg, List<Tuple<String, String>> args) {
        return cfg;
    }

    public static void main(String[] args) throws IOException {
        // Pull off the first arg and the remainder is our options
        Tuple<Optional<String>, Optional<String[]>> ht = ArgHelper.headAndTail(args);
        String path = ht.first.orElse("/tmp/default.yaml");
        args = ht.second.orElse(args);

        // Our starting value
        YAMLConfigurator yCfg = YAMLConfigurator.build(path);
        Config next;
        next = yCfg.pipe(yCfg.config, null);
        next = CLIConfigurator.build(next).pipe(next, ICLIConfig.arrayToTupleList(args));
        yCfg.logger.info("done");

        Config cfg = ICIBus.getConfigFromPath(Config.class, path)
                .orElseThrow(() -> new NoConfigFoundError(String.format("Could not load configuration file at %s", path)));
        cfg.getBrokers();
    }
}
