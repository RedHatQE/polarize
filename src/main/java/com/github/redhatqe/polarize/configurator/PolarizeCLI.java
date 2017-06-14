package com.github.redhatqe.polarize.configurator;

import com.github.redhatqe.byzantine.config.IConfig;
import com.github.redhatqe.byzantine.config.Serializer;
import com.github.redhatqe.byzantine.configurator.ICLIConfig;
import com.github.redhatqe.byzantine.parser.Option;
import com.github.redhatqe.byzantine.utils.ArgHelper;
import com.github.redhatqe.byzantine.utils.Tuple;
import com.github.redhatqe.polarize.configuration.PolarizeConfig;
import com.github.redhatqe.polarize.configuration.PolarizeConfigOpts;
import com.github.redhatqe.polarize.reporter.configuration.ReporterConfigOpts;
import com.github.redhatqe.polarize.reporter.configurator.ReporterCLI;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.github.redhatqe.polarize.configuration.PolarizeConfigOpts.*;

/**
 * All the info to change
 */
public class PolarizeCLI extends ReporterCLI {
    public PolarizeConfig cfg;
    private OptionParser parser = new OptionParser();
    private Map<PolarizeConfigOpts, Option<String>> sOptions = new HashMap<>();
    private Map<PolarizeConfigOpts, Option<Boolean>> bOptions = new HashMap<>();
    private Map<PolarizeConfigOpts, Option<Integer>> iOptions = new HashMap<>();
    private Map<String, OptionSpec<String>> sOptToSpec = new HashMap<>();
    private Map<String, OptionSpec<Boolean>> bOptToSpec = new HashMap<>();
    private Map<String, OptionSpec<Integer>> iOptToSpec = new HashMap<>();
    public Logger logger = LogManager.getLogger("PolarizeCLI");

    public PolarizeCLI(PolarizeConfig cfg) {
        super(cfg);
    }

    public void setupNameToHandler(PolarizeConfig iConfig) {
        super.setupNameToHandler(iConfig);
        this.sOptions.put(TC_SELECTOR_NAME, this.setOption(TC_SELECTOR_NAME));
        this.sOptions.put(TC_SELECTOR_VAL, this.setOption(TC_SELECTOR_VAL));
        this.sOptions.put(TESTCASE_PREFIX, this.setOption(TESTCASE_PREFIX));
        this.sOptions.put(TESTCASE_SUFFIX, this.setOption(TESTCASE_SUFFIX));
        this.bOptions.put(TC_IMPORTER_ENABLED, this.setOption(TC_IMPORTER_ENABLED));
        this.iOptions.put(TC_IMPORTER_TIMEOUT, this.setOption(TC_IMPORTER_TIMEOUT));

        this.setDispatchHandlers(sOptions, sOptToSpec, iConfig.sGetHandlers(), String.class);
        this.setDispatchHandlers(bOptions, bOptToSpec, iConfig.bGetHandlers(), Boolean.class);
        this.setDispatchHandlers(iOptions, iOptToSpec, iConfig.iGetHandlers(), Integer.class);
    }

    public <T> Option<T> setOption(PolarizeConfigOpts cfg) {
        return new Option<>(cfg.getOption(), cfg.getDesc());
    }

    public PolarizeConfig pipe(PolarizeConfig reporterConfig, List<Tuple<String, String>> list) {
        PolarizeConfig copied = new PolarizeConfig(reporterConfig);
        copied.setupDefaultHandlers();
        this.setupNameToHandler(copied);
        String[] a = ICLIConfig.tupleListToArray(list);
        this.parse(copied, a);
        return copied;
    }

    public static void main(String... args) throws IOException {
        Tuple<Optional<String>, Optional<String[]>> ht = ArgHelper.headAndTail(args);
        String polarizeConfig;
        File cfgFile;
        // If the first arg doesn't start with --, the first arg is the config path, otherwise, use the default
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
        PolarizeConfig ymlCfg = Serializer.fromYaml(PolarizeConfig.class, cfgFile);
        ymlCfg.setupDefaultHandlers();
        PolarizeCLI cliFig = new PolarizeCLI(ymlCfg);

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
                , "--edit-config", "/tmp/testing-polarize-config.yaml"
                , "--current-xunit", "https://rhsm-jenkins.rhev-ci-vms.eng.rdu2.redhat.com/view/QE-RHEL7.4/job/import-polarion-testrun/lastSuccessfulBuild/artifact/new-testng-polarion.xml"
                , "--new-xunit", "/tmp/modified-polarion.xml"
        };
        if (args.length == 0)
            args = testArgs;

        PolarizeConfig afterCLICfg = cliFig.pipe(ymlCfg, ICLIConfig.arrayToTupleList(args));
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
