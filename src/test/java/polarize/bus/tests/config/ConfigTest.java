package polarize.bus.tests.config;


import com.github.redhatqe.byzantine.configuration.Serializer;
import com.github.redhatqe.polarize.configuration.Broker;
import com.github.redhatqe.polarize.configuration.Config;
import cucumber.api.CucumberOptions;
import cucumber.api.java8.En;
import cucumber.api.junit.Cucumber;
import org.junit.Assert;
import org.junit.runner.RunWith;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import polarize.bus.tests.Helper;

import java.io.*;


@RunWith(Cucumber.class)
@CucumberOptions( plugin = "json:target/cucumber-report.json"
                , features = {"src/test/resources"})
public class ConfigTest implements En {
    static Config config;
    static String configPath;
    static Logger logger = LogManager.getLogger(ConfigTest.class);

    public ConfigTest() {
        // Copies the default.yaml file in resources/configs/default.yaml to /tmp
        Given("^the default config file exists in /tmp/default\\.yaml$", () -> {
            configPath = "/tmp/default.yaml";
            Helper.installDefaultConfig(configPath);
        });

        // Sets this.config
        When("^a user loads the config file$", ()-> {
            try {
                config = Serializer.fromYaml(Config.class, new File(configPath));
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        Then("^the config file should be loaded successfully$", ()-> {
            Assert.assertTrue(config != null);
        });

        And("^the (\\w+)\\.(\\w+) should be '(.+)'$", (String name, String key, String ans)-> {
            Broker b = config.getBrokers().get(name);
            Assert.assertTrue(b != null);
            switch(key) {
                case "url":
                    Assert.assertTrue(b.getUrl().equals(ans));
                    break;
                default:
                    throw new AssertionError("key is not recognized");
            }
        });
    }
}
