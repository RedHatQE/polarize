package com.github.redhatqe.polarize.configurator;

import com.github.redhatqe.byzantine.configurator.IENVConfig;
import com.github.redhatqe.byzantine.utils.Tuple;
import com.github.redhatqe.polarize.configuration.BrokerConfig;

import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class ENVConfigurator implements IENVConfig {
    public BrokerConfig cfg;
    public Map<String, String> recognizedVars = new HashMap<>();

    public ENVConfigurator(BrokerConfig cfg) {
        this.cfg = cfg;
    }

    @Override
    public void setupDefaultVars() {
        this.recognizedVars.put("DEFAULT_BROKER", "");
    }

    @Override
    public String pipe(String cfg, List<Tuple<String, String>> args) {
        return null;
    }
}
