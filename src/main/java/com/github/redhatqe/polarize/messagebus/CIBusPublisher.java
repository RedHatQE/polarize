package com.github.redhatqe.polarize.messagebus;

import com.github.redhatqe.byzantine.configurator.ICLIConfig;
import com.github.redhatqe.byzantine.exceptions.NoConfigFoundError;
import com.github.redhatqe.byzantine.utils.ArgHelper;
import com.github.redhatqe.byzantine.utils.Tuple;
import com.github.redhatqe.polarize.configuration.Broker;
import com.github.redhatqe.polarize.configuration.Config;
import com.github.redhatqe.polarize.configurator.CLIConfigurator;
import com.github.redhatqe.polarize.configurator.YAMLConfigurator;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.*;
import java.io.IOException;
import java.util.Optional;

/**
 * Publishes messages to the central CI Message Bus
 *
 */
public class CIBusPublisher implements ICIBus {
    private Logger logger;
    public Config config;
    private Broker broker;
    private String topic;
    private String clientID;
    private String configPath;
    private static Integer id = 0;

    public synchronized static Integer getId() {
        return CIBusPublisher.id++;
    }

    public CIBusPublisher() {
        this(ICIBus.getDefaultConfigPath());
    }

    public CIBusPublisher(String path) {
        this.logger = LoggerFactory.getLogger(CIBusListener.class);
        this.topic = "CI";
        this.clientID = "Polarize-" + Integer.toString(CIBusPublisher.getId());
        this.configPath = path;
        this.config = ICIBus.getConfigFromPath(Config.class, this.configPath).orElseThrow(() -> {
            return new NoConfigFoundError(String.format("Could not find configuration file at %s", this.configPath));
        });
        if (this.config != null)
            this.broker = this.config.getBrokers().get(this.config.getDefaultBroker());
    }

    public CIBusPublisher(String name, String id, String url, String user, String pw, Long timeout, Integer max) {
        this();
        this.clientID = id;
        this.config = new Config(name, url, user, pw, timeout, max);
        this.broker = this.config.getBrokers().get(name);
    }

    public CIBusPublisher(Config cfg) {
        this();
        if (cfg != null)
            this.config = cfg;
        else
            this.config = ICIBus.getConfigFromPath(Config.class, this.configPath).orElseThrow(() -> {
                return new NoConfigFoundError(String.format("Could not find configuration file at %s", this.configPath));
            });
        if (this.config != null)
            this.broker = this.config.getBrokers().get(this.config.getDefaultBroker());
    }

    public static void setOptionals(Message msg, JMSMessageOptions opts) {
        if (!opts.jmsType.equals(""))
            try {
                msg.setJMSType(opts.jmsType);
            } catch (JMSException e) {
                e.printStackTrace();
            }

        opts.props.forEach((k, v) -> {
            try {
                msg.setStringProperty(k, v);
            } catch (JMSException e) {
                e.printStackTrace();
            }
        });
    }

    public Optional<Connection>
    sendMessage(String text, Broker broker, JMSMessageOptions opts) {
        return this.sendMessage(text, broker.getUrl(), broker.getUser(), broker.getPassword(), opts);
    }

    public Optional<Connection>
    sendMessage(String text, String url, String user, String pw, JMSMessageOptions opts) {
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(url);
        Connection connection = null;
        MessageProducer producer;

        factory.setUserName(user);
        factory.setPassword(pw);
        try {
            connection = factory.createConnection();
            connection.setClientID(this.clientID);
            connection.setExceptionListener(exc -> this.logger.error(exc.getMessage()));

            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            // FIXME: Ideally, we should have the Topic figured out via JNDI
            Topic dest = session.createTopic("CI");
            producer = session.createProducer(dest);
            TextMessage msg = session.createTextMessage(text);
            setOptionals(msg, opts);

            producer.send(msg, opts.mode, opts.priority, opts.ttl);
        } catch (JMSException e) {
            e.printStackTrace();
        }
        return Optional.ofNullable(connection);
    }


    public static void main(String[] args) throws IOException {
        // Pull off the first arg and the remainder is our options
        Tuple<Optional<String>, Optional<String[]>> ht = ArgHelper.headAndTail(args);
        String path = ht.first.orElse(ICIBus.getDefaultConfigPath());
        args = ht.second.orElse(args);

        // Get our configuration.  Create the file based configuration, then any environment settings, and finally CLI args
        YAMLConfigurator yCfg = YAMLConfigurator.build(path);
        Config next;
        next = yCfg.pipe(yCfg.config, null);
        next = CLIConfigurator.build(next).pipe(next, ICLIConfig.arrayToTupleList(args));

        CIBusPublisher pub = new CIBusPublisher(next);
        pub.configPath = path;
        JMSMessageOptions opts = new JMSMessageOptions("stoner-bus-test");
        opts.addProperty("my_private_field", "sean_toner");
        //opts.addProperty("tests", "[1, 2, 3]");

        String body = "{ \"test\": 100 }";
        Optional<Connection> maybeCon = pub.sendMessage(body, pub.broker.getUrl(), pub.broker.getUser(),
                pub.broker.getPassword(), opts);
        maybeCon.ifPresent(con -> {
            try {
                con.close();
            } catch (JMSException e) {
                e.printStackTrace();
            }
        });
    }
}
