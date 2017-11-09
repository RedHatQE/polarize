package com.github.redhatqe.polarize.configurator;

import com.github.redhatqe.byzantine.configuration.IConfig;
import com.github.redhatqe.byzantine.configurator.ICLIConfig;
import com.github.redhatqe.byzantine.configurator.IConfigurator;
import com.github.redhatqe.byzantine.utils.Tuple;
import com.github.redhatqe.polarize.configuration.BrokerConfig;
import joptsimple.OptionSet;


import java.util.List;

/**
 * This class
 */
public class JsonConfigurator implements IConfigurator<BrokerConfig>, ICLIConfig {

    /**
     * This is most likely the initial starting Configurator stage in the pipeline.  The pipeline generally will start
     * with reading in a configuration file of some sort, followed by a remote configuration, environment variables, then CLI options.
     *
     * @param cfg
     * @param args
     * @return
     */
    @Override
    public BrokerConfig pipe(BrokerConfig cfg, List<Tuple<String, String>> args) {
        return null;
    }

    @Override
    public <T1 extends IConfig> void setupNameToHandler(T1 cfg) {

    }

    @Override
    public Boolean printHelp(OptionSet opts) {
        return null;
    }

    @Override
    public void parse(IConfig cfg, String... args) {

    }
}
