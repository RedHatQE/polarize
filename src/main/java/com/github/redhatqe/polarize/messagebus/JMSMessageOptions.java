package com.github.redhatqe.polarize.messagebus;

import javax.jms.DeliveryMode;
import java.util.HashMap;
import java.util.Map;

/**
 * Various settings for the JMS message
 */
public class JMSMessageOptions {
    String jmsType = "";
    Map<String, String> props = new HashMap<>();
    Integer mode = DeliveryMode.NON_PERSISTENT;
    Integer priority = 3;
    Long ttl = 180000L;

    public JMSMessageOptions(String type, Map<String, String> properties) {
        this.jmsType = type;
        this.props = properties;
    }

    public JMSMessageOptions(String type) {
        this.jmsType = type;
    }

    public void addProperty(String key, String val) {
        this.props.put(key, val);
    }
}
