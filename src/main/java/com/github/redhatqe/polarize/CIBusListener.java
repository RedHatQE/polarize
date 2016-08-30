package com.github.redhatqe.polarize;

import com.github.redhatqe.polarize.exceptions.ConfigurationError;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.Session;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Enumeration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * Created by stoner on 8/30/16.
 */
public class CIBusListener {
    private Map<String, String> polarizeConfig;
    private Logger logger;

    public CIBusListener(Map<String, String> config) {
        this.polarizeConfig = config;
        this.logger = LoggerFactory.getLogger(CIBusListener.class);
    }

    private Optional<Connection> tapIntoMessageBus() {
        String brokerUrl = this.polarizeConfig.get("broker");
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(brokerUrl);
        Connection connection;
        try {
            factory.setUserName(this.polarizeConfig.get("kerb.user"));
            factory.setPassword(this.polarizeConfig.get("kerb.pass"));
            connection = factory.createConnection();
            connection.setClientID("polarize");

            // FIXME: need to understand how transactions affect session
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            // FIXME: Do I need a Queue?  From the docs that appears to be point-to-point.  Maybe DurableScheduler?
            javax.jms.Queue queue = session.createQueue("/topic/CI");
            String responseName = this.polarizeConfig.get("xunit.importer.response.name");
            if (responseName == null || responseName.equals("")) {
                this.logger.error("Must supply a value for xunit.importer.response.name");
                throw new ConfigurationError();
            }
            MessageConsumer consumer = session.createConsumer(queue, responseName);

            // FIXME: We need to have some way to know when we see our message.
            consumer.setMessageListener(msg -> {
                try {
                    Enumeration props = msg.getPropertyNames();
                    while(props.hasMoreElements()) {
                        Object p = props.nextElement();
                        this.logger.info(String.format("%s: %s", p.toString(), msg.getStringProperty(p.toString())));
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
        return Optional.of(connection);
    }

    /**
     * Does 2 things: launches tapIntoMessageBus from a Fork/Join pool thread and the main thread waits for user to quit
     *
     * @param args
     */
    public static void main(String[] args) throws ExecutionException, InterruptedException, JMSException {
        CIBusListener bl = new CIBusListener(Configurator.loadConfiguration());

        CompletableFuture<Optional<Connection>> future;
        future = CompletableFuture.supplyAsync(bl::tapIntoMessageBus);

        // Call to get() will block.  However, tapIntoMessageBus() will return immediately.
        Optional<Connection> maybeConn = future.get();
        if (!maybeConn.isPresent()) {
            bl.logger.error("No Connection object found");
            return;
        }

        // The main thread loop
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
        Connection conn = maybeConn.get();
        conn.close();
    }
}
