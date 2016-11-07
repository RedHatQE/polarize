package com.github.redhatqe.polarize.services;

/**
 * The IServiceListener is a protocol that makes the underlying transport mechanism agnostic.
 *
 * For example, there might be a JMSServiceListener that listens for the message using JMS messages, and there might be
 * a HTTPServiceListener that listens for POST requests.
 */
public interface IServiceListener {
    RequestMessage listen();
    Boolean postToRequestQueue(RequestMessage msg);
}
