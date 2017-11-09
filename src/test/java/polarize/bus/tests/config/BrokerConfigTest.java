package polarize.bus.tests.config;


import com.github.redhatqe.byzantine.configuration.Serializer;
import com.github.redhatqe.polarize.configuration.Broker;
import com.github.redhatqe.polarize.configuration.BrokerConfig;
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
public class BrokerConfigTest implements En {
    static BrokerConfig brokerConfig;
    static String configPath;
    static Logger logger = LogManager.getLogger(BrokerConfigTest.class);

    public BrokerConfigTest() {
        // Copies the default.yaml file in resources/configs/default.yaml to /tmp
        Given("^the default brokerConfig file exists in /tmp/default\\.yaml$", () -> {
            configPath = "/tmp/default.yaml";
            Helper.installDefaultConfig(configPath);
        });

        // Sets this.brokerConfig
        When("^a user loads the brokerConfig file$", ()-> {
            try {
                brokerConfig = Serializer.fromYaml(BrokerConfig.class, new File(configPath));
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        Then("^the brokerConfig file should be loaded successfully$", ()-> {
            Assert.assertTrue(brokerConfig != null);
        });

        And("^the (\\w+)\\.(\\w+) should be '(.+)'$", (String name, String key, String ans)-> {
            Broker b = brokerConfig.getBrokers().get(name);
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
