package com.github.redhatqe.polarize;

import com.fasterxml.jackson.core.type.TypeReference;
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
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

/**
 * Created by stoner on 8/30/16.
 */
public class CIBusListener {
    public Map<String, String> polarizeConfig;
    public Logger logger;

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
            factory.setUserName(this.polarizeConfig.get("kerb.user"));
            factory.setPassword(this.polarizeConfig.get("kerb.pass"));
            connection = factory.createConnection();
            connection.setClientID("polarize");
            connection.setExceptionListener(exc -> this.logger.error(exc.getMessage()));

            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Topic dest = session.createTopic("CI");

            if (selector == null || selector.equals("")) {
                this.logger.error("Must supply a value for the selector");
                throw new ConfigurationError();
            }
            connection.start();
            consumer = session.createConsumer(dest, selector);
            String timeout = this.polarizeConfig.getOrDefault("importer.timeout", "600000");
            msg = consumer.receive(Integer.parseInt(timeout));

        } catch (JMSException e) {
            e.printStackTrace();
            return Optional.empty();
        }
        Tuple<Connection, Message> tuple = new Tuple<>();
        tuple.first = connection;
        tuple.second = msg;
        return Optional.of(tuple);
    }

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
        Tuple<Connection, ObjectNode> tuple = new Tuple<>();
        tuple.first = connection;
        tuple.second = root;
        return Optional.of(tuple);
    }

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
            this.logger.error(String.format("Unknown Message:  Could not read message %s", msg.toString()));
        }
        return root;
    }

    /**
     * Does 2 things: launches tapIntoMessageBus from a Fork/Join pool thread and the main thread waits for user to quit
     *
     * @param args
     */
    public static void main(String[] args) throws ExecutionException, InterruptedException, JMSException {
        CIBusListener bl = new CIBusListener(Configurator.loadConfiguration());
        //String responseName = bl.polarizeConfig.get("importer.testcase.response.name");
        //String responseName = bl.polarizeConfig.get("importer.xunit.response.name");
        String responseName = args[0];

        //CompletableFuture<Optional<Connection>> future;
        //future = CompletableFuture.supplyAsync(bl::tapIntoMessageBus);

        // Call to get() will block.  However, tapIntoMessageBus() will return immediately.
        //Optional<Tuple<Connection, ObjectNode>> maybeConn = bl.tapIntoMessageBus(responseName);

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

        // For tapIntoMessage()
        //Tuple<Connection, ObjectNode> tuple = maybeConn.get();
        //ObjectNode node = tuple.second;
        tuple.first.close();
    }
}
