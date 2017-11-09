package com.github.redhatqe.polarize.messagebus;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.redhatqe.polarize.configuration.Broker;
import com.github.redhatqe.polarize.configuration.BrokerConfig;
import io.reactivex.subjects.Subject;
import org.apache.commons.collections4.queue.CircularFifoQueue;

import java.util.UUID;

public abstract class CIBusClient {

    protected String clientID;
    protected String configPath;
    protected BrokerConfig brokerConfig;
    protected Broker broker;
    protected Subject<ObjectNode> nodeSub;
    protected Integer messageCount = 0;
    protected UUID uuid;
    public CircularFifoQueue<MessageResult> messages;

    public static final String POLARIZE_CLIENT_ID = "client-polarize";
    public static final String TOPIC = "VirtualTopic.qe.ci.>";

}
