package com.github.redhatqe.polarize.messagebus;

import com.github.redhatqe.byzantine.configurator.ICLIConfig;
import com.github.redhatqe.byzantine.exceptions.NoConfigFoundError;
import com.github.redhatqe.byzantine.utils.ArgHelper;
import com.github.redhatqe.byzantine.utils.Tuple;
import com.github.redhatqe.polarize.configuration.Broker;
import com.github.redhatqe.polarize.configuration.BrokerConfig;
import com.github.redhatqe.polarize.configuration.PolarizeConfig;
import com.github.redhatqe.polarize.configurator.CLIConfigurator;
import com.github.redhatqe.polarize.configurator.YAMLConfigurator;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.ActiveMQMessageProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.*;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

/**
 * Publishes messages to the central CI Message Bus
 *
 */
public class CIBusPublisher extends CIBusClient implements ICIBus {
    private Logger logger = LoggerFactory.getLogger(CIBusListener.class);
    private String publishDest;
    public static final String DEFAULT_PUBLISH_DEST = "VirtualTopic.qe.ci.jenkins";

    public String getPublishDest() {
        return publishDest;
    }

    public void setPublishDest(String publishDest) {
        this.publishDest = publishDest;
    }

    public CIBusPublisher() {
        this("");
    }

    public CIBusPublisher(String path) {
        this.uuid = UUID.randomUUID();
        this.clientID =  POLARIZE_CLIENT_ID;
        this.publishDest = DEFAULT_PUBLISH_DEST;
        if (path.equals(""))
            this.configPath = ICIBus.getDefaultConfigPath();
        this.configPath = path;
        this.brokerConfig = ICIBus.getConfigFromPath(BrokerConfig.class, this.configPath).orElseThrow(() -> {
            return new NoConfigFoundError(String.format("Could not find configuration file at %s", this.configPath));
        });

        this.broker = this.brokerConfig.getBrokers().get(this.brokerConfig.getDefaultBroker());
    }

    public CIBusPublisher(String name, String id, String url, String user, String pw, Long timeout, Integer max) {
        this();
        this.clientID = id;
        this.brokerConfig = new BrokerConfig(name, url, user, pw, timeout, max);
        this.broker = this.brokerConfig.getBrokers().get(name);
    }

    public CIBusPublisher(BrokerConfig cfg) {
        this.uuid = UUID.randomUUID();
        this.clientID =  POLARIZE_CLIENT_ID;
        this.publishDest = DEFAULT_PUBLISH_DEST;
        this.configPath = "";
        if (cfg != null)
            this.brokerConfig = cfg;
        else
            this.brokerConfig = ICIBus.getConfigFromPath(BrokerConfig.class, this.configPath).orElseThrow(() -> {
                return new NoConfigFoundError(String.format("Could not find configuration file at %s", this.configPath));
            });
        this.broker = this.brokerConfig.getBrokers().get(this.brokerConfig.getDefaultBroker());
    }

    public CIBusPublisher(PolarizeConfig cfg) {
        this.uuid = UUID.randomUUID();
        this.clientID =  POLARIZE_CLIENT_ID;
        this.publishDest = DEFAULT_PUBLISH_DEST;
        this.configPath = "";
        if (cfg != null)
            this.brokerConfig = cfg.getMessageBus();
        else
            this.brokerConfig = ICIBus.getConfigFromPath(BrokerConfig.class, this.configPath).orElseThrow(() -> {
            String msg = String.format("Couldn't find configuration file at %s", this.configPath);
                return new NoConfigFoundError(msg);
            });
        if (this.brokerConfig != null)
            this.broker = this.brokerConfig.getBrokers().get(this.brokerConfig.getDefaultBroker());
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
        return this.sendMessage(text, broker.getUrl(), opts);
    }

    public Optional<Connection>
    sendMessage(String text, String url, JMSMessageOptions opts) {
        ActiveMQConnectionFactory factory = this.setupFactory(url, this.broker);
        Connection connection = null;
        MessageProducer producer;

        try {
            connection = factory.createConnection();
            connection.setClientID(this.clientID);
            connection.setExceptionListener(exc -> this.logger.error(exc.getMessage()));

            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            // FIXME: Ideally, we should have the Topic figured out via JNDI
            Topic dest = session.createTopic(this.publishDest);
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
        BrokerConfig next;
        next = yCfg.pipe(yCfg.brokerConfig, null);
        next = CLIConfigurator.build(next).pipe(next, ICLIConfig.arrayToTupleList(args));

        CIBusPublisher pub = new CIBusPublisher(next);
        pub.configPath = path;
        JMSMessageOptions opts = new JMSMessageOptions("stoner-bus-test");
        opts.addProperty("my_private_field", "sean_toner");

        String body = "{ \"test\": 100 }";
        Optional<Connection> maybeCon = pub.sendMessage(body, pub.broker.getUrl(), opts);
        maybeCon.ifPresent(con -> {
            try {
                con.close();
            } catch (JMSException e) {
                e.printStackTrace();
            }
        });
    }
}
