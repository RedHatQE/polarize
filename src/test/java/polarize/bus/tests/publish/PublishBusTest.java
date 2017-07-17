package polarize.bus.tests.publish;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.github.redhatqe.byzantine.configuration.Serializer;
import com.github.redhatqe.polarize.configuration.Broker;
import com.github.redhatqe.polarize.configuration.Config;
import com.github.redhatqe.polarize.messagebus.*;
import cucumber.api.CucumberOptions;
import cucumber.api.java8.En;
import cucumber.api.junit.Cucumber;
import org.junit.After;
import org.junit.Assert;
import org.junit.runner.RunWith;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import polarize.bus.tests.Helper;

import javax.jms.Connection;
import javax.jms.JMSException;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Need to figure out how to specify tags for cucumber. Especially so that gradle will pick it up
 * Also, this should really be a unit test not a unit test that gets run by gradle
 *
@RunWith(Cucumber.class)
@CucumberOptions( plugin = "json:target/cucumber-report.json"
                , features = {"src/test/resources/publish.feature"})
*/
public class PublishBusTest implements En {
    public Config config;
    static String configPath = Helper.getDefaultConfigPath();
    static Logger logger = LogManager.getLogger("messagebus." + PublishBusTest.class.getName());
    public String body = "";
    public JMSMessageOptions opts;
    public Optional<Connection> conn;
    public CIBusListener cbl;
    public String selector = "";
    public MessageResult result;

    public PublishBusTest() {

        Given("^a JSON message \"body\" with the following$", (String body) -> {
            this.body = body;
        });

        And("^in the header there is a (\\w+) with a value of (\\w+)$", (String k, String v) -> {
            Map<String, String> props = new HashMap<>();
            props.put(k, v);
            this.opts = new JMSMessageOptions("stoner-polarize-bus-test", props);
        });

        And("the default config file is used", () -> {
            //Helper.installDefaultConfig(configPath);
            try {
                config = Serializer.fromYaml(Config.class, new File(configPath));
            } catch (IOException e) {
                throw new Error(e.getMessage());
            }
        });

        When("^the JMS selector is set to (\\w+)='(\\w+)'$", (String k, String v) -> {
            this.selector = String.format("%s='%s'", k, v);
            MessageHandler hdlr = IMessageListener.defaultHandler();
            // FIXME: Use guice to make something that is an IMessageListener so we can mock it out
            this.cbl = new CIBusListener(hdlr, this.config);
            this.cbl.tapIntoMessageBus(selector, this.cbl.createListener(this.cbl.messageParser()));
        });

        And("^the message is sent to the (\\w+) url$", (String ci) -> {
            Assert.assertTrue(this.config.getBrokers().containsKey(ci));
            Broker b = this.config.getBrokers().get(ci);
            b.setMessageMax(1);
            CIBusPublisher cbp = new CIBusPublisher(this.config);
            this.conn = cbp.sendMessage(this.body, b, this.opts);
            Assert.assertTrue(this.conn.isPresent());
        });

        Then("^the message should be received with the reply body of$", 30000, (String body) -> {
            this.cbl.listenUntil(10000L);
            Assert.assertTrue(this.cbl.messages.size() == 1);
            result = this.cbl.messages.remove();
            if (result.getNode().isPresent()) {
                ObjectNode node = result.getNode().get();
                ObjectMapper mapper = new ObjectMapper();
                try {
                    JsonNode testNode = mapper.readTree(body);
                    String expected = testNode.get("testing").textValue();
                    JsonNode testing = node.get("root");
                    String actual = testing.get("testing").textValue();
                    Assert.assertTrue(expected.equals(actual));
                } catch (IOException e) {
                    Assert.fail("Invalid Test: The expected value in the test did not convert to a Json object");
                }
            }
            else
                Assert.fail("No message node");
        });

        And("^the message result status should be \"(\\w+)\"$", (String expected) -> {
            Assert.assertTrue(this.result.getStatus().toString().equals(expected));
        });
    }

    @After
    public void cleanup() {
        this.conn.ifPresent(c -> {
            try {
                c.close();
            } catch (JMSException e) {
                e.printStackTrace();
            }
        });
    }
}
