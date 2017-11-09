package com.github.redhatqe.polarize.configurator;


import com.github.redhatqe.byzantine.configuration.IConfig;
import com.github.redhatqe.byzantine.configuration.Serializer;
import com.github.redhatqe.byzantine.configurator.ICLIConfig;
import com.github.redhatqe.byzantine.configurator.IConfigurator;
import com.github.redhatqe.byzantine.parser.Option;
import com.github.redhatqe.byzantine.utils.ArgHelper;
import com.github.redhatqe.byzantine.utils.Tuple;
import com.github.redhatqe.polarize.configuration.Broker;
import com.github.redhatqe.polarize.configuration.BrokerConfig;
import com.github.redhatqe.polarize.configuration.ConfigOpts;
import joptsimple.ArgumentAcceptingOptionSpec;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;


public class CLIConfigurator implements IConfigurator<BrokerConfig>, ICLIConfig {
    private OptionParser parser = new OptionParser();
    private Map<String, Option<String>> sOptions = new HashMap<>();
    private Map<String, OptionSpec<String>> optToSpec = new HashMap<>();
    public static String cfgEnvName = "POLARIZE_CONFIG";
    public static final String configFileName = "broker-config.yaml";
    private BrokerConfig brokerConfig;
    public Logger logger = LogManager.getLogger("byzantine-" + CLIConfigurator.class.getName());

    public CLIConfigurator(BrokerConfig cfg) {
        this.brokerConfig = cfg;
    }

    public static CLIConfigurator build(BrokerConfig cfg) {
        return new CLIConfigurator(cfg);
    }


    /**
     * This is where all the cli options this data type accepts are given.  The cfg object we pass in will call its
     * dispatch method given the name of some option which will look up in one of its handler maps and return the
     * method used to set the value
     */
    @Override
    public <T1 extends IConfig> void setupNameToHandler(T1 cfg) {
        String brokerDesc = "Sets a broker type.  The value is in the form of key.field=value.  Example:" +
                "--broker ci.url=192.168.0.1.  This may be specified several times";
        this.sOptions.put(ConfigOpts.BROKER, new Option<>(ConfigOpts.BROKER, brokerDesc));
        String defBrokerDesc = "The default broker to use when no specific broker is given";
        this.sOptions.put(ConfigOpts.DEFAULT_BROKER, new Option<>(ConfigOpts.DEFAULT_BROKER, defBrokerDesc));
        String editCfgDesc = "Save the configuration to a file";
        this.sOptions.put(ConfigOpts.EDIT_CONFIG, new Option<>(ConfigOpts.EDIT_CONFIG, editCfgDesc));
        String help = "Prints out help for all command line options";
        this.optToSpec.put(ConfigOpts.HELP, this.parser.accepts(ConfigOpts.HELP, help)
                .withOptionalArg().ofType(String.class)
                .describedAs("Show help"));

        this.sOptions.forEach((k, o) -> {
            ArgumentAcceptingOptionSpec<String> spec =
                    this.parser.accepts(o.opt, o.description).withRequiredArg().ofType(String.class);
            if (o.defaultTo != null)
                spec.defaultsTo(o.defaultTo);
            optToSpec.put(o.opt, spec);

            o.setter = cfg.dispatch(o.opt, cfg.sGetHandlers());
        });
    }

    @Override
    public Boolean printHelp(OptionSet opts) {
        OptionSpec<String> helpSpec = this.optToSpec.get(ConfigOpts.HELP);
        Boolean help = false;
        if (opts.has(helpSpec)) {
            help = true;
            try {
                this.parser.printHelpOn(System.out);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return help;
    }

    public void writeConfig(OptionSet opts) {
        OptionSpec<String> editSpec = this.optToSpec.get(ConfigOpts.EDIT_CONFIG);
        if (opts.has(editSpec)) {
            String val = opts.valueOf(editSpec);
            Option<String> opt = this.sOptions.get(ConfigOpts.EDIT_CONFIG);
            if (opt != null)
                opt.setter.set(val);
            else
                logger.info("Could not find edit-configuration setter in sOptions");
        }
    }

    @Override
    public void parse(IConfig cfg, String... args) {
        OptionSet opts = this.parser.parse(args);

        // Help has to be done first
        if(this.printHelp(opts)) {
            cfg.setHelp(true);
            return;
        }

        this.sOptions.forEach((k, o) -> {
            if (!k.equals(ConfigOpts.EDIT_CONFIG)) {
                OptionSpec<String> spec = this.optToSpec.get(k);
                if (opts.has(spec)) {
                    String val;
                    try {
                        val = opts.valueOf(spec);
                        o.setter.set(val);
                    }
                    catch (Exception ex) {
                        List<String> vals = opts.valuesOf(spec);
                        vals.forEach(v -> o.setter.set(v));
                    }
                }
            }
        });

        // Edit configuration has to be done last
        this.writeConfig(opts);
    }

    @Override
    public BrokerConfig pipe(BrokerConfig cfg, List<Tuple<String, String>> args) {
        BrokerConfig copied = new BrokerConfig(cfg);
        this.setupNameToHandler(copied);
        String[] a = ICLIConfig.tupleListToArray(args);
        this.parse(cfg, a);
        copied.getBrokers().get("ci").setUser("humdinger");
        return copied;
    }


    /**
     * TODO: throw this into a test
     * @param args
     */
    public static void test(String... args) {
        String[] testArgs = {"--broker", "ci.url=192.168.100.100"};
        BrokerConfig cfg2 = new BrokerConfig("ci", "ci-labs-foo", "stoner", "bar", 60000L, 1);
        Broker b = new Broker("ci-labs.eng.rdu2:61613", "foo", "bar", 1000L, 1);
        cfg2.addBroker("metrics", b);
        CLIConfigurator cliCfg = new CLIConfigurator(cfg2);

        List<Tuple<String, String>> argList = ICLIConfig.arrayToTupleList(testArgs);
        BrokerConfig modified = cliCfg.pipe(cfg2, argList);
        cliCfg.logger.info("Done");
    }

    public static String getConfigFromEnvOrDefault() {
        String cfg;
        String homeDir = System.getProperty("user.home");
        // Try to get from environment
        if (System.getenv().containsKey(cfgEnvName))
            cfg = System.getenv().get(cfgEnvName);
        else
            cfg = FileSystems.getDefault()
                    .getPath(homeDir + String.format("/.polarize/%s", configFileName)).toString();
        return cfg;
    }

    public static void main(String... args) throws IOException {
        Tuple<Optional<String>, Optional<String[]>> ht = ArgHelper.headAndTail(args);
        String polarizeConfig;
        File cfgFile;
        // If the first arg doesn't start with --, the first arg is the configuration path, otherwise, use the default
        if (ht.first.isPresent()) {
            if (ht.first.get().startsWith("--"))
                polarizeConfig = getConfigFromEnvOrDefault();
            else
                polarizeConfig = ht.first.get();
        }
        else
            polarizeConfig = getConfigFromEnvOrDefault();

        // Start with the BrokerConfig from the YAML then pipe it to the CLI
        cfgFile = new File(polarizeConfig);
        if (!cfgFile.exists())
            throw new IOException(String.format("%s does not exist", polarizeConfig));
        BrokerConfig ymlCfg = Serializer.fromYaml(BrokerConfig.class, cfgFile);
        CLIConfigurator cliFig = new CLIConfigurator(ymlCfg);
        BrokerConfig afterCLICfg = cliFig.pipe(ymlCfg, ICLIConfig.arrayToTupleList(args));

    }
}
