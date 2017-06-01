package com.github.redhatqe.polarize.services;

import com.github.redhatqe.polarize.messagebus.CIBusListener;

/**
 * This class is designed to be a little background service always listening for a message for a Request
 *
 * The main use case scenarios are the following:
 * - XUnit import request
 * - TestCase import request
 * - Modified xunit request
 *
 * A message will be passed from a client to a certain topic.  A master thread dispatcher will listen for incoming
 * messages for each request type.  As the message is received, it will post the request to a queue.  Another thread
 * acting as the service handler will pull requests off this queue.  As the service handler completes what it needs to
 * do, it will push the response message back to a Response Queue.  The Response Queue is a general object that multiple
 * ServiceHandlers can push messages to.  However, there is one Listener, RequestQueue and ServiceHandler per request
 * type.
 *
 * +-----------+       +---------------+       +-----------------+       +-------------------+
 * | listener  |------>| Request Queue |------>| Service Handler |------>| Polarion endpoint |
 * +-----------+       +---------------+       +-----------------+       +-------------------+
 *                                                      |                           |
 *                                                puts req into                sends response
 *                                                      |                           |
 *                                                      V                           V
 *                                             +------------------+      +--------------------+
 *                                             | OrientDB Pending |<-----| Message Listener   |
 *                                             +------------------+      +--------------------+
 *
 * The JMSServiceListener is the JMS implementation of the IServiceListener.  The JSON that is received comes from
 * listening on the CI Message Bus.  This is opposed to for example an HTTPServiceListener or WebSocketServiceListener.
 */
public class JMSServiceListener implements IServiceListener{
    public RequestQueue queue;
    public CIBusListener cbl;

    public JMSServiceListener() {
        this.cbl = new CIBusListener();
        this.queue = new RequestQueue();
    }

    /**
     *
     * @return
     */
    @Override
    public RequestMessage listen() {
        return null;
    }

    @Override
    public Boolean postToRequestQueue(RequestMessage msg) {
        return null;
    }
}
