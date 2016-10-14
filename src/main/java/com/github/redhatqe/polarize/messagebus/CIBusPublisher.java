package com.github.redhatqe.polarize.messagebus;

import com.github.redhatqe.polarize.Configurator;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.*;
import java.util.Map;

/**
 * Publishes messages to the central CI Message Bus
 *
 */
public class CIBusPublisher {
    private Map<String, String> polarizeConfig;
    private Logger logger;

    public CIBusPublisher() {
        this.polarizeConfig = Configurator.loadConfiguration();
        this.logger = LoggerFactory.getLogger(CIBusListener.class);
    }

    public void sendMessage(String text) {
        String brokerUrl = this.polarizeConfig.get("broker");
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(brokerUrl);
        Connection connection;
        MessageProducer producer;

        String user = this.polarizeConfig.get("kerb.user");
        String pw = this.polarizeConfig.get("kerb.pass");
        factory.setUserName(user);
        factory.setPassword(pw);
        try {
            connection = factory.createConnection();
            connection.setClientID("polarize");
            connection.setExceptionListener(exc -> this.logger.error(exc.getMessage()));

            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            // FIXME: Ideally, we should have the Topic figured out via JNDI
            Topic dest = session.createTopic("CI");
            producer = session.createProducer(dest);
            TextMessage msg = session.createTextMessage(text);
            producer.send(msg, DeliveryMode.NON_PERSISTENT, 3, 180000L);
        } catch (JMSException e) {
            e.printStackTrace();
        }
    }
}
