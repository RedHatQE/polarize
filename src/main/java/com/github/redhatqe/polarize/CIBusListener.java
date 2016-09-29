package com.github.redhatqe.polarize;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.redhatqe.polarize.exceptions.ConfigurationError;
import com.github.redhatqe.polarize.utils.Tuple;
import org.apache.activemq.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.*;
import javax.jms.Message;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Enumeration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

/**
 * A Class that provides functionality to listen to the CI Message Bus
 */
public class CIBusListener {
    Map<String, String> polarizeConfig;
    private Logger logger;

    public CIBusListener(Map<String, String> config) {
        this.polarizeConfig = config;
        this.logger = LoggerFactory.getLogger(CIBusListener.class);
    }

    public Optional<Tuple<Connection, Message>> waitForMessage(String selector) {
        String brokerUrl = this.polarizeConfig.get("broker");
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(brokerUrl);
        Connection connection;
        MessageConsumer consumer;
        Message msg;

        try {
            String user = this.polarizeConfig.get("kerb.user");
            String pw = this.polarizeConfig.get("kerb.pass");
            factory.setUserName(user);
            factory.setPassword(pw);
            connection = factory.createConnection();
            connection.setClientID("polarize");
            connection.setExceptionListener(exc -> this.logger.error(exc.getMessage()));

            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Topic dest = session.createTopic("CI");

            if (selector == null || selector.equals("")) {
                this.logger.error("Must supply a value for the selector");
                throw new ConfigurationError();
            }
            this.logger.debug(String.format("Using selector of:\n%s", selector));
            connection.start();
            consumer = session.createConsumer(dest, selector);
            String timeout = this.polarizeConfig.getOrDefault("importer.timeout", "600000");
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
     * @param selector
     * @return
     */
    public Optional<Tuple<Connection, ObjectNode>> tapIntoMessageBus(String selector) {
        String brokerUrl = this.polarizeConfig.get("broker");
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(brokerUrl);
        Connection connection;
        MessageConsumer consumer;
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = mapper.createObjectNode();
        try {
            factory.setUserName(this.polarizeConfig.get("kerb.user"));
            factory.setPassword(this.polarizeConfig.get("kerb.pass"));
            connection = factory.createConnection();
            connection.setClientID("polarize");
            connection.setExceptionListener(exc -> this.logger.error(exc.getMessage()));

            // FIXME: need to understand how transactions affect session
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Topic dest = session.createTopic("CI");

            if (selector == null || selector.equals("")) {
                this.logger.error("Must supply a value for selector");
                throw new ConfigurationError();
            }
            consumer = session.createConsumer(dest, selector);

            // FIXME: We need to have some way to know when we see our message.
            consumer.setMessageListener(msg -> {
                try {
                    Enumeration props = msg.getPropertyNames();
                    while(props.hasMoreElements()) {
                        Object p = props.nextElement();
                        this.logger.info(String.format("%s: %s", p.toString(), msg.getStringProperty(p.toString())));
                    }
                    if (msg instanceof MapMessage) {
                        MapMessage mm = (MapMessage) msg;
                        Enumeration names = mm.getMapNames();
                        while(names.hasMoreElements()) {
                            String p = (String) names.nextElement();
                            String field = mm.getStringProperty(p);
                            root.set(field, mapper.convertValue(mm.getObject(field), JsonNode.class));
                        }
                    }
                }
                catch (JMSException e) {
                    System.err.println("Error reading message");
                }
            });

            connection.start();
        } catch (JMSException e) {
            e.printStackTrace();
            return Optional.empty();
        }
        Tuple<Connection, ObjectNode> tuple = new Tuple<>(connection, root);
        return Optional.of(tuple);
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
        }
        else if (msg instanceof TextMessage) {
            TextMessage tm = (TextMessage) msg;
            String text = tm.getText();
            this.logger.info(text);
            try {
                JsonNode node = mapper.readTree(text);
                root.set("root", node);
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
     * Returns a Supplier usable for a CompleteableFuture object
     *
     * @return ObjectNode that is the parsed message
     */
    public static Supplier<Optional<ObjectNode>> getCIMessage(String selector) {
        return () -> {
            ObjectNode root = null;
            CIBusListener bl = new CIBusListener(com.github.redhatqe.polarize.Configurator.loadConfiguration());
            Optional<Tuple<Connection, Message>> maybeConn = bl.waitForMessage(selector);
            if (!maybeConn.isPresent()) {
                bl.logger.error("No Connection object found");
                return Optional.empty();
            }

            Tuple<Connection, Message> tuple = maybeConn.get();
            Connection conn = tuple.first;
            Message msg = tuple.second;

            // FIXME:  Should I write an exception handler outside of this function?  Might be easier than trying to
            // deal with it here (for example a retry)
            try {
                conn.close();
                root = bl.parseMessage(msg);
            } catch (ExecutionException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (JMSException e) {
                e.printStackTrace();
            }
            if (root != null)
                return Optional.of(root);
            else
                return Optional.empty();
        };
    }

    /**
     * Does 2 things: launches tapIntoMessageBus from a Fork/Join pool thread and the main thread waits for user to quit
     *
     * Takes one argument: a string that will be used as the JMS Selector
     *
     * @param args
     */
    public static void main(String[] args) throws ExecutionException, InterruptedException, JMSException {
        CIBusListener bl = new CIBusListener(Configurator.loadConfiguration());
        String responseName = args[0];

        // waitForMessage will block here until we get a message or we time out
        Optional<Tuple<Connection, Message>> maybeConn = bl.waitForMessage(responseName);
        if (!maybeConn.isPresent()) {
            bl.logger.error("No Connection object found");
            return;
        }

        Tuple<Connection, Message> tuple = maybeConn.get();
        Message msg = tuple.second;
        ObjectNode root = bl.parseMessage(msg);

        Boolean stop = false;
        while(!stop) {
            // Get keyboard input.  When the user types 'q'. stop the loop
            InputStreamReader r=new InputStreamReader(System.in);
            BufferedReader br=new BufferedReader(r);
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
