package com.github.redhatqe.polarize.messagebus;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.github.redhatqe.byzantine.exceptions.NoConfigFoundError;
import com.github.redhatqe.byzantine.utils.ArgHelper;
import com.github.redhatqe.byzantine.utils.Tuple;
import com.github.redhatqe.polarize.configuration.Broker;
import com.github.redhatqe.polarize.configuration.Config;

import com.github.redhatqe.polarize.configuration.PolarizeConfig;
import com.github.redhatqe.polarize.reporter.configuration.ServerInfo;
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import java.time.Instant;
import java.util.Enumeration;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

/**
 * A Class that provides functionality to listen to the CI Message Bus
 */
public class CIBusListener implements ICIBus, IMessageListener {
    Logger logger;
    private String topic;
    private String clientID;
    private String configPath;
    public Config config;
    private Broker broker;
    private Subject<ObjectNode> nodeSub;
    private Integer messageCount = 0;
    private static Integer id = 0;
    // TODO: Implement a RingBuffer or double ended queue to hold received messages
    public CircularFifoQueue<MessageResult> messages;

    private synchronized static Integer getId(){
        return CIBusListener.id++;
    }

    public CIBusListener() {
        this.logger = LogManager.getLogger("messagebus.CIBusListener");
        this.topic = "CI";
        this.clientID = "polarize-bus-listener-" + Integer.toString(CIBusListener.getId());
        this.configPath = ICIBus.getDefaultConfigPath();
        String err = String.format("Could not find configuration file at %s", this.configPath);
        this.config = ICIBus.getConfigFromPath(Config.class, this.configPath)
                .orElseThrow(() -> new NoConfigFoundError(err));
        if (this.config != null)
            this.broker = this.config.getBrokers().get(this.config.getDefaultBroker());

        this.messages = new CircularFifoQueue<>(20);
        this.nodeSub = this.setupDefaultSubject(IMessageListener.defaultHandler());
    }

    public CIBusListener(MessageHandler hdlr) {
        this();
        this.nodeSub = this.setupDefaultSubject(hdlr);
    }

    public CIBusListener(MessageHandler hdlr, String path) {
        this(hdlr);
        this.config = ICIBus.getConfigFromPath(Config.class, path).orElseThrow(() -> {
            return new NoConfigFoundError(String.format("Could not find configuration file at %s", this.configPath));
        });
    }

    public CIBusListener(MessageHandler hdlr, String name, String id, String url, String user, String pw,
                         Long timeout, Integer max) {
        this(hdlr);
        this.clientID = id;
        this.config = new Config(name, url, user, pw, timeout, max);
        this.broker = this.config.getBrokers().get(name);
    }

    public CIBusListener(MessageHandler hdlr, Config cfg) {
        this(hdlr);
        if (cfg != null)
            this.config = cfg;
        else
            this.config = ICIBus.getConfigFromPath(Config.class, this.configPath).orElseThrow(() -> {
                return new NoConfigFoundError(String.format("Could not find configuration file at %s", this.configPath));
            });
        if (this.config != null)
            this.broker = this.config.getBrokers().get(this.config.getDefaultBroker());
    }

    public CIBusListener(MessageHandler hdlr, PolarizeConfig cfg) {
        this.logger = LogManager.getLogger("messagebus.CIBusListener");
        this.topic = "CI";
        this.clientID = "polarize-bus-listener-" + Integer.toString(CIBusListener.getId());
        this.configPath = "";
        this.config = cfg.getMessageBus();
        if (this.config != null)
            this.broker = this.config.getBrokers().get(this.config.getDefaultBroker());

        this.messages = new CircularFifoQueue<>(20);
        this.nodeSub = this.setupDefaultSubject(IMessageListener.defaultHandler());
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
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(brokerUrl);
        Connection connection = null;
        MessageConsumer consumer;

        try {
            String user = this.broker.getUser();
            String pw = this.broker.getPassword();
            factory.setUserName(user);
            factory.setPassword(pw);
            connection = factory.createConnection();
            connection.setClientID(this.clientID);
            connection.setExceptionListener(exc -> this.logger.error(exc.getMessage()));

            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Topic dest = session.createTopic("CI");
            if (selector == null || selector.equals(""))
                throw new Error("Must supply a value for the selector");

            consumer = session.createConsumer(dest, selector);

            // FIXME: We need to have some way to know when we see our message.
            consumer.setMessageListener(listener);
            connection.start();
        } catch (JMSException e) {
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
     * This is an asynchronous single-threaded way of listening for messages.  It uses tapIntoMessageBus to supply a
     * MessageListener.  Each time the listener is called, it will call onNext to the Subject, pushing an ObjectNode
     * @param args
     */
    public static void main(String... args) throws JMSException {
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
                    logger.info("Still waiting for message...");
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
        CIBusListener bl = new CIBusListener();
        String responseName = args[0];

        // waitForMessage will block here until we get a message or we time out
        Optional<Tuple<Connection, Message>> maybeConn = bl.waitForMessage(responseName);
        if (!maybeConn.isPresent()) {
            bl.logger.error("No Connection object found");
            return;
        }

        Tuple<Connection, Message> tuple = maybeConn.get();
        Message msg = tuple.second;

        Boolean stop = false;
        while(!stop) {
            // Get keyboard input.  When the user types 'q'. stop the loop
            InputStreamReader r = new InputStreamReader(System.in);
            BufferedReader br = new BufferedReader(r);
            System.out.println("Enter 'q' to quit");
            try {
                String answer = br.readLine();
                if (answer.toLowerCase().charAt(0) == 'q') {
                    stop = true;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        tuple.first.close();
    }
}
