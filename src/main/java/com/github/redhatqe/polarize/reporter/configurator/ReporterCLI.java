package com.github.redhatqe.polarize.reporter.configurator;

import com.github.redhatqe.byzantine.configuration.IConfig;
import com.github.redhatqe.byzantine.configuration.Serializer;
import com.github.redhatqe.byzantine.configurator.ICLIConfig;
import com.github.redhatqe.byzantine.configurator.IConfigurator;
import com.github.redhatqe.byzantine.parser.Option;
import com.github.redhatqe.byzantine.parser.Setter;
import com.github.redhatqe.byzantine.utils.ArgHelper;
import com.github.redhatqe.byzantine.utils.Tuple;
import com.github.redhatqe.polarize.reporter.configuration.IGetOpts;
import com.github.redhatqe.polarize.reporter.configuration.ReporterConfig;
import com.github.redhatqe.polarize.reporter.configuration.ReporterConfigOpts;
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

import static com.github.redhatqe.polarize.reporter.configuration.ReporterConfigOpts.*;


public class ReporterCLI implements IConfigurator<ReporterConfig>, ICLIConfig {
    public static final String cfgEnvName = "POLARIZE_CONFIG";
    public static String configFileName = "polarize-config.yaml";
    protected OptionParser parser = new OptionParser();
    public Map<IGetOpts, Option<String>> sOptions = new HashMap<>();
    public Map<IGetOpts, Option<Boolean>> bOptions = new HashMap<>();
    public Map<IGetOpts, Option<Integer>> iOptions = new HashMap<>();
    public Map<String, OptionSpec<String>> sOptToSpec = new HashMap<>();
    public Map<String, OptionSpec<Boolean>> bOptToSpec = new HashMap<>();
    public Map<String, OptionSpec<Integer>> iOptToSpec = new HashMap<>();
    public Logger logger = LogManager.getLogger(ReporterCLI.class.getName());
    private final ReporterConfig cfg;

    public ReporterCLI(ReporterConfig cfg) {
        this.cfg = cfg;
    }

    public ReporterCLI() {
        this.cfg = new ReporterConfig();
    }

    public Map<IGetOpts, Option<String>> getSOptions() {
        return this.sOptions;
    }

    public Map<IGetOpts, Option<Boolean>> getBOptions() {
        return this.bOptions;
    }

    public Map<IGetOpts, Option<Integer>> getIOptions() {
        return this.iOptions;
    }

    public void setSOptions(Map<IGetOpts, Option<String>> opts) {
        this.sOptions = opts;
    }

    public void setBOptions(Map<IGetOpts, Option<Boolean>> opts) {
        this.bOptions = opts;
    }

    public void setIOptions(Map<IGetOpts, Option<Integer>> opts) {
        this.iOptions = opts;
    }

    public Map<String, OptionSpec<String>> getSOptToSpec() {
        return this.sOptToSpec;
    }

    public Map<String, OptionSpec<Boolean>> getBOptToSpec() {
        return this.bOptToSpec;
    }

    public Map<String, OptionSpec<Integer>> getIOptToSpec() {
        return this.iOptToSpec;
    }

    public void setSOptToSpec(Map<String, OptionSpec<String>> specs) {
        this.sOptToSpec = specs;
    }

    public void setBOptToSpec(Map<String, OptionSpec<Boolean>> specs) {
        this.bOptToSpec = specs;
    }

    public void setIOptToSpec(Map<String, OptionSpec<Integer>> specs) {
        this.iOptToSpec = specs;
    }

    /**
     * This is where all the cli options this data type accepts are given.  The cfg object we pass in will call its
     * dispatch method given the name of some option which will look up in one of its handler maps and return the
     * method used to set the value
     */
    @Override
    public <T extends IConfig> void setupNameToHandler(T t) {
        this.sOptions.put(ARCH, this.setOption(ARCH));
        this.sOptions.put(BASE_DIR, this.setOption(BASE_DIR));
        this.sOptions.put(CURRENT_XUNIT, this.setOption(CURRENT_XUNIT));
        this.sOptions.put(NEW_XUNIT, this.setOption(NEW_XUNIT));
        this.sOptions.put(MAPPING, this.setOption(MAPPING));
        this.sOptions.put(CONFIG, this.setOption(CONFIG));
        this.sOptions.put(HELP, this.setOption(HELP));
        this.sOptions.put(PROJECT, this.setOption(PROJECT));
        this.sOptions.put(TESTRUN_ID, this.setOption(TESTRUN_ID));
        this.sOptions.put(TESTRUN_GROUP_ID, this.setOption(TESTRUN_GROUP_ID));
        this.sOptions.put(TESTRUN_TEMPLATE_ID, this.setOption(TESTRUN_TEMPLATE_ID));
        this.sOptions.put(TESTRUN_TITLE, this.setOption(TESTRUN_TITLE));
        this.sOptions.put(TESTRUN_TYPE, this.setOption(TESTRUN_TYPE));
        this.sOptions.put(XUNIT_SELECTOR_NAME, this.setOption(XUNIT_SELECTOR_NAME));
        this.sOptions.put(XUNIT_SELECTOR_VAL, this.setOption(XUNIT_SELECTOR_VAL));
        this.sOptions.put(USERNAME, this.setOption(USERNAME));
        this.sOptions.put(USERPASSWORD, this.setOption(USERPASSWORD));
        this.sOptions.put(EDIT_CONFIG, this.setOption(EDIT_CONFIG));
        // Non-standard string CLI options
        this.sOptions.put(ReporterConfigOpts.TR_PROPERTY, this.setOption(TR_PROPERTY));
        this.sOptions.put(ReporterConfigOpts.SERVER, this.setOption(SERVER));

        this.bOptions.put(XUNIT_IMPORTER_ENABLED, this.setOption(XUNIT_IMPORTER_ENABLED));
        this.bOptions.put(TR_DRY_RUN, this.setOption(TR_DRY_RUN));
        this.bOptions.put(TR_INCLUDE_SKIPPED, this.setOption(TR_INCLUDE_SKIPPED));
        this.bOptions.put(TR_SET_FINISHED, this.setOption(TR_SET_FINISHED));

        this.iOptions.put(XUNIT_IMPORTER_TIMEOUT, this.setOption(XUNIT_IMPORTER_TIMEOUT));

        this.setDispatchHandlers(sOptions, sOptToSpec, t.sGetHandlers(), String.class);
        this.setDispatchHandlers(bOptions, bOptToSpec, t.bGetHandlers(), Boolean.class);
        this.setDispatchHandlers(iOptions, iOptToSpec, t.iGetHandlers(), Integer.class);

        String help = "Prints out help for all command line options";
        this.sOptToSpec.put(HELP.getOption(), this.parser.accepts(HELP.getOption(), help)
                .withOptionalArg().ofType(String.class)
                .describedAs("Show help"));
    }

    @Override
    public Boolean printHelp(OptionSet opts) {
        OptionSpec<String> helpSpec = this.sOptToSpec.get(HELP.getOption());
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

    public OptionParser getParser() { return this.parser; }

    /**
     *
     * @param strings
     */
    @Override
    public void parse(IConfig cfg, String... strings) {
        OptionSet opts = this.parser.parse(strings);

        // Help has to be done first
        if(this.printHelp(opts)) {
            cfg.setHelp(true);
            return;
        }

        this.optionParser(opts, this.sOptions, this.sOptToSpec);
        this.optionParser(opts, this.bOptions, this.bOptToSpec);
        this.optionParser(opts, this.iOptions, this.iOptToSpec);

        // Edit configuration has to be done last
        this.writeConfig(opts);
    }

    public void writeConfig(OptionSet opts) {
        OptionSpec<String> editSpec = this.sOptToSpec.get(ReporterConfigOpts.EDIT_CONFIG.getOption());
        if (!opts.has(editSpec))
            return;
        String val = opts.valueOf(editSpec);
        Option<String> opt = this.sOptions.get(ReporterConfigOpts.EDIT_CONFIG);
        if (opt != null)
            opt.setter.set(val);
        else
            logger.info("Could not find edit-configuration setter in sOptions");
        try {
            Serializer.toYaml(this.cfg, val);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public ReporterConfig pipe(ReporterConfig reporterConfig, List<Tuple<String, String>> list) {
        ReporterConfig copied = new ReporterConfig(reporterConfig);
        copied.setupDefaultHandlers();
        this.setupNameToHandler(copied);
        String[] a = ICLIConfig.tupleListToArray(list);
        this.parse(copied, a);
        return copied;
    }


    // FIXME: should this be an interface on IConfigurator?
    protected <T> Option<T> setOption(ReporterConfigOpts cfg) {
        return new Option<>(cfg.getOption(), cfg.getDesc());
    }

    /**
     * Sets up the parser to recognize the CLI options passed in.  All of the Option types will have their setter
     * mapped during this method call.
     *
     * @param options
     * @param optToSpec
     * @param handlers
     * @param cls
     * @param <T>
     */
    protected <C, T> void
    setDispatchHandlers(Map<C, Option<T>> options,
                        Map<String, OptionSpec<T>> optToSpec,
                        Map<String, Setter<T>> handlers,
                        Class<T> cls) {
        options.forEach((C k, Option<T> o) -> {
            ArgumentAcceptingOptionSpec<T> spec =
                    this.parser.accepts(o.opt, o.description).withRequiredArg().ofType(cls);
            if (o.defaultTo != null)
                spec.defaultsTo(o.defaultTo);
            optToSpec.put(o.opt, spec);

            o.setter = this.cfg.dispatch(o.opt, handlers);
        });
    }

    protected <C extends IGetOpts, T> void
    optionParser(OptionSet opts,
                 Map<C, Option<T>> opOpts,
                 Map<String, OptionSpec<T>> optToSpec) {
        opOpts.forEach((k, o) -> {
            if (!k.getOption().equals("edit-configuration")) {
                OptionSpec<T> spec = optToSpec.get(k.getOption());
                if (opts.has(spec)) {
                    T val;
                    try {
                        val = opts.valueOf(spec);
                        o.setter.set(val);
                    }
                    catch (Exception ex) {
                        List<T> vals = opts.valuesOf(spec);
                        vals.forEach(v -> o.setter.set(v));
                    }
                }
            }
        });
    }

    /**
     * // FIXME: Should this be a static method in the IConfig or ICLIConfigurator interfaces?
     * @return
     */
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

        // Start with the Config from the YAML then pipe it to the CLI
        cfgFile = new File(polarizeConfig);
        if (!cfgFile.exists())
            throw new IOException(String.format("%s does not exist", polarizeConfig));
        ReporterConfig ymlCfg = Serializer.fromYaml(ReporterConfig.class, cfgFile);
        ymlCfg.setupDefaultHandlers();
        ReporterCLI cliFig = new ReporterCLI(ymlCfg);

        String[] testArgs = { "--property", "arch=ppc64"
                , "--testrun-title", "foobarbaz"
                , "--project", "RedHatEnterpriseLinux7"
                , "--testrun-type", "buildacceptance"
                , "--testrun-group-id", "rhsmqe"
                , "--base-dir", "/tmp/foo"
                , "--testrun-template-id", "sean toner test template"
                , "--property", "plannedin=RH7_4_Snap3"
                , "--property", "notes='just some notes here"
                , "--property", "jenkinsjobs='http://path/to/job/url"
                , "--edit-configuration", "/tmp/testing-polarize-config.yaml"
                , "--current-xunit", "https://rhsm-jenkins.rhev-ci-vms.eng.rdu2.redhat.com/view/QE-RHEL7.4/job/import-polarion-testrun/lastSuccessfulBuild/artifact/new-testng-polarion.xml"
                , "--new-xunit", "/tmp/modified-polarion.xml"
        };
        if (args.length == 0)
            args = testArgs;

        ReporterConfig afterCLICfg = cliFig.pipe(ymlCfg, ICLIConfig.arrayToTupleList(args));
        if (afterCLICfg.showHelp)
            return;
        String currentXunit = afterCLICfg.getCurrentXUnit();
        String newXunit = afterCLICfg.getNewXunit();
        if (currentXunit!= null && newXunit != null)
            afterCLICfg.editTestSuite(currentXunit, newXunit);

        String editPath = afterCLICfg.getNewConfigPath();
        if (!editPath.equals(""))
            Serializer.toYaml(afterCLICfg, editPath);
    }

}
