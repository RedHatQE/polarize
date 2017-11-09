package com.github.redhatqe.polarize.messagebus;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.github.redhatqe.byzantine.exceptions.NoConfigFoundError;
import com.github.redhatqe.byzantine.utils.ArgHelper;
import com.github.redhatqe.byzantine.utils.Tuple;
import com.github.redhatqe.polarize.configuration.Broker;
import com.github.redhatqe.polarize.configuration.BrokerConfig;

import com.github.redhatqe.polarize.configuration.PolarizeConfig;
import io.reactivex.functions.Action;
import io.reactivex.functions.Consumer;
import io.reactivex.subjects.PublishSubject;
import io.reactivex.subjects.Subject;
import org.apache.activemq.*;
import org.apache.commons.collections4.queue.CircularFifoQueue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.jms.*;
import javax.jms.Message;
import javax.jms.Queue;

import java.io.IOException;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutionException;

/**
 * A Class that provides functionality to listen to the CI Message Bus
 */
public class CIBusListener extends CIBusClient implements ICIBus, IMessageListener {
    Logger logger = LogManager.getLogger(CIBusListener.class.getName());
    private String topic;
    private Subject<ObjectNode> nodeSub;
    private Integer messageCount = 0;

    public String publishDest;
    public CircularFifoQueue<MessageResult> messages;


    public CIBusListener() {
        super();
        this.topic = TOPIC;
        this.uuid = UUID.randomUUID();
        this.clientID = POLARIZE_CLIENT_ID + "." + this.uuid;

        this.configPath = ICIBus.getDefaultConfigPath();
        String err = String.format("Could not find configuration file at %s", this.configPath);
        this.brokerConfig = ICIBus.getConfigFromPath(BrokerConfig.class, this.configPath)
                .orElseThrow(() -> new NoConfigFoundError(err));
        this.broker = this.brokerConfig.getBrokers().get(this.brokerConfig.getDefaultBroker());

        this.messages = new CircularFifoQueue<>(20);
        this.nodeSub = this.setupDefaultSubject(IMessageListener.defaultHandler());
    }

    public CIBusListener(MessageHandler hdlr) {
        this();
        this.nodeSub = this.setupDefaultSubject(hdlr);
    }

    public CIBusListener(MessageHandler hdlr, String path) {
        this(hdlr);
        this.brokerConfig = ICIBus.getConfigFromPath(BrokerConfig.class, path).orElseThrow(() -> {
            return new NoConfigFoundError(String.format("Could not find configuration file at %s", this.configPath));
        });
    }

    public CIBusListener(MessageHandler hdlr, String name, String id, String url, String user, String pw,
                         Long timeout, Integer max) {
        this(hdlr);
        this.clientID = id;
        this.brokerConfig = new BrokerConfig(name, url, user, pw, timeout, max);
        this.broker = this.brokerConfig.getBrokers().get(name);
    }

    public CIBusListener(MessageHandler hdlr, BrokerConfig cfg) {
        this(hdlr);
        if (cfg != null)
            this.brokerConfig = cfg;
        else
            this.brokerConfig = ICIBus.getConfigFromPath(BrokerConfig.class, this.configPath).orElseThrow(() -> {
                return new NoConfigFoundError(String.format("Could not find brokerConfig file at %s", this.configPath));
            });

        this.broker = this.brokerConfig.getBrokers().get(this.brokerConfig.getDefaultBroker());
    }

    public CIBusListener(MessageHandler hdlr, PolarizeConfig cfg) {
        this.logger = LogManager.getLogger("messagebus.CIBusListener");
        this.topic = TOPIC;
        this.clientID = POLARIZE_CLIENT_ID + "." + UUID.randomUUID();
        this.configPath = "";
        this.brokerConfig = cfg.getMessageBus();
        if (this.brokerConfig != null)
            this.broker = this.brokerConfig.getBrokers().get(this.brokerConfig.getDefaultBroker());

        this.messages = new CircularFifoQueue<>(20);
        this.nodeSub = this.setupDefaultSubject(hdlr);
    }

    /**
     * Creates a Subject with a default set of onNext, onError, and onComplete handlers
     *
     * @param handler A MessageHandler that will be applied by the subscriber
     * @return A Subject which will pass the Object node along
     */
    private Subject<ObjectNode> setupDefaultSubject(MessageHandler handler) {
        Consumer<ObjectNode> next = (ObjectNode node) -> {
            MessageResult result = handler.handle(node);
            // FIXME: I dont like storing state like this, but onNext doesn't return anything
            this.messageCount++;
            this.messages.add(result);
        };
        Action act = () -> {
            logger.info("Stop listening!");
            this.messageCount = 0;
        };
        // FIXME: use DI to figure out what kind of Subject to create, ie AsyncSubject, BehaviorSubject, etc
        Subject<ObjectNode> n = PublishSubject.create();
        n.subscribe(next, Throwable::printStackTrace, act);
        return n;
    }

    /**
     * Creates a default listener for MapMessage types
     *
     * @param parser a MessageParser lambda that will be applied to the MessageListener
     * @return a MessageListener lambda
     */
    @Override
    public MessageListener createListener(MessageParser parser) {
        return msg -> {
            try {
                ObjectNode node = parser.parse(msg);
                // Since nodeSub is a Subject, the call to onNext will pass through the node object to itself
                this.nodeSub.onNext(node);
            } catch (ExecutionException | InterruptedException | JMSException e) {
                this.nodeSub.onError(e);
            }
        };
    }

    /**
     * A synchronous blocking call to receive a message from the message bus
     *
     * @param selector the JMS selector to get a message from a topic
     * @return An optional tuple of the session connection and the Message object
     */
    @Override
    public Optional<Tuple<Connection, Message>> waitForMessage(String selector) {
        String brokerUrl = this.broker.getUrl();
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(brokerUrl);
        Connection connection;
        MessageConsumer consumer;
        Message msg;

        try {
            String user = this.broker.getUser();
            String pw = this.broker.getPassword();
            factory.setUserName(user);
            factory.setPassword(pw);
            connection = factory.createConnection();
            connection.setClientID(this.clientID);
            connection.setExceptionListener(exc -> this.logger.error(exc.getMessage()));

            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Topic dest = session.createTopic(this.topic);

            if (selector == null || selector.equals(""))
                throw new Error("Must supply a value for the selector");

            this.logger.debug(String.format("Using selector of:\n%s", selector));
            connection.start();
            consumer = session.createConsumer(dest, selector);
            String timeout = this.broker.getMessageTimeout().toString();
            msg = consumer.receive(Integer.parseInt(timeout));

        } catch (JMSException e) {
            e.printStackTrace();
            return Optional.empty();
        }
        Tuple<Connection, Message> tuple = new Tuple<>(connection, msg);
        return Optional.of(tuple);
    }


    /**
     * An asynchronous way to get a Message with a MessageListener
     *
     * @param selector String to use for JMS selector
     * @param listener a MessageListener to be passed to the Session
     * @return an Optional Connection to be used for closing the session
     */
    @Override
    public Optional<Connection> tapIntoMessageBus(String selector, MessageListener listener) {
        String brokerUrl = this.broker.getUrl();
        ActiveMQConnectionFactory factory = this.setupFactory(brokerUrl, this.broker);
        Connection connection = null;
        MessageConsumer consumer;

        try {
            connection = factory.createConnection();
            connection.setClientID(this.clientID);
            connection.setExceptionListener(exc -> this.logger.error(exc.getMessage()));

            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

            String publishDest = String.format("Consumer.%s.%s", POLARIZE_CLIENT_ID + ".queue", TOPIC);
            Queue dest = session.createQueue(publishDest);

            if (selector == null || selector.equals(""))
                throw new Error("Must supply a value for the selector");

            consumer = session.createConsumer(dest, selector);

            // FIXME: We need to have some way to know when we see our message.
            consumer.setMessageListener(listener);
            connection.start();
        } catch (JMSException e) {
            e.printStackTrace();
        } catch (Exception e) {
            logger.error("Error getting keystore");
            e.printStackTrace();
        }
        return Optional.ofNullable(connection);
    }

    public MessageParser messageParser() {
        return this::parseMessage;
    }

    /**
     * Parses a Message returning a Jackson ObjectNode
     *
     * @param msg Message received from a Message bus
     * @return
     * @throws ExecutionException
     * @throws InterruptedException
     * @throws JMSException
     */
    @Override
    public ObjectNode parseMessage(Message msg) throws ExecutionException, InterruptedException, JMSException  {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = mapper.createObjectNode();
        if (msg instanceof MapMessage) {
            MapMessage mm = (MapMessage) msg;
            Enumeration names = mm.getMapNames();
            while(names.hasMoreElements()) {
                String p = (String) names.nextElement();
                String field = mm.getStringProperty(p);
                root.set(field, mapper.convertValue(mm.getObject(field), JsonNode.class));
            }
            return root;
        }
        else if (msg instanceof TextMessage) {
            TextMessage tm = (TextMessage) msg;
            String text = tm.getText();
            this.logger.info(text);
            try {
                JsonNode node = mapper.readTree(text);
                root.set("root", node);  // FIXME: this is hacky
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        else {
            String err = msg == null ? " was null" : msg.toString();
            this.logger.error(String.format("Unknown Message:  Could not read message %s", err));
        }
        return root;
    }

    /**
     * Overrides the broker's timeout value with the given timeout and count
     *
     * Loop will stop once either the timeout has expired or the number of messages of reached is received
     *
     * @param timeout number of milliseconds to wait
     * @param count number of
     */
    public void listenUntil(Long timeout, Integer count) {
        Long start = Instant.now().getEpochSecond();
        Long end = start + (timeout / 1000);
        Instant endtime = Instant.ofEpochSecond(end);
        int mod = 0;
        logger.info("Begin listening for message.  Times out at " + endtime.toString());
        while(this.messageCount < count && Instant.now().getEpochSecond() < end) {
            try {
                Thread.sleep(1000);
                mod++;
                if (mod % 10 == 0)
                    logger.info(String.format("Waiting on message for %d seconds...", mod));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        this.nodeSub.onComplete();
    }

    public void listenUntil() {
        this.listenUntil(this.broker.getMessageTimeout(), this.broker.getMessageMax());
    }

    /**
     * Overrides the broker's default message timeout
     *
     * @param timeout number of milliseconds before timing out
     */
    public void listenUntil(Long timeout) {
        this.listenUntil(timeout, this.broker.getMessageMax());
    }

    /**
     * Overrides the broker's default message max
     *
     * @param count number of messages to get before quitting
     */
    public void listenUntil(Integer count) {
        this.listenUntil(this.broker.getMessageTimeout(), count);
    }

    /**
     * Does 2 things: launches waitForMessage from a Fork/Join pool thread and the main thread waits for user to quit
     *
     * Takes one argument: a string that will be used as the JMS Selector
     *
     * @param args
     */
    public static void test(String[] args) throws ExecutionException, InterruptedException, JMSException {
        // FIXME: Use guice to make something that is an IMessageListener so we can mock it out
        CIBusListener bl = new CIBusListener();
        Optional<Connection> rconn = bl.tapIntoMessageBus("rhsm_qe=polarize_bus",
                bl.createListener(bl.messageParser()));

        Broker b = bl.brokerConfig.getBrokers().get("ci");
        b.setMessageMax(1);
        CIBusPublisher cbp = new CIBusPublisher(bl.brokerConfig);
        String body = "{ \"testing\": \"Hello World\"}";

        Map<String, String> props = new HashMap<>();
        props.put("rhsm_qe", "polarize_bus");
        Optional<Connection> sconn = cbp.sendMessage(body, b, new JMSMessageOptions("stoner-polarize", props));

        bl.listenUntil(10000L);
        MessageResult result = bl.messages.remove();
        if (result.getNode().isPresent()) {
            ObjectNode node = result.getNode().get();
            ObjectMapper mapper = new ObjectMapper();
            try {
                JsonNode testNode = mapper.readTree(body);
                String expected = testNode.get("testing").textValue();
                JsonNode testing = node.get("root");
                String actual = testing.get("testing").textValue();
            } catch (IOException e) {
                bl.logger.error("Invalid Test: The expected value in the test did not convert to a Json object");
            }
        }
        else
            bl.logger.error("No message node");

        rconn.ifPresent((Connection c) -> {
            try {
                c.close();
            } catch (JMSException e) {
                e.printStackTrace();
            }
        });

        sconn.ifPresent(c -> {
            try {
                c.close();
            } catch (JMSException e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * This is an asynchronous single-threaded way of listening for messages.  It uses tapIntoMessageBus to supply a
     * MessageListener.  Each time the listener is called, it will call onNext to the Subject, pushing an ObjectNode
     * @param args
     */
    public static void main2(String... args) throws JMSException {
        MessageHandler hdlr = IMessageListener.defaultHandler();
        CIBusListener cbl = new CIBusListener(hdlr);

        Tuple<Optional<String>, Optional<String[]>> ht = ArgHelper.headAndTail(args);
        String cfgPath = ht.first.orElse(ICIBus.getDefaultConfigPath());
        CIBusListener bl = new CIBusListener(IMessageListener.defaultHandler(), cfgPath);
        String selector = ht.second.orElseGet(() -> new String[]{"polarize_bus=\"testing\""})[0];

        Optional<Connection> res = bl.tapIntoMessageBus(selector, bl.createListener(bl.messageParser()));

        // Spin in a loop here.  We will stop once we get the max number of messages as specified from the broker
        // setting, or the timeout has been exceeded.  Sleep every second so we dont kill CPU usage.
        bl.listenUntil(30000L);

        if (res.isPresent())
            res.get().close();
    }

    public static void main(String[] args) {
        try {
            CIBusListener.test(args);
        } catch (ExecutionException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (JMSException e) {
            e.printStackTrace();
        }
    }
}
