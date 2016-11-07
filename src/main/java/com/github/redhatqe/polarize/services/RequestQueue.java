package com.github.redhatqe.polarize.services;

import java.util.concurrent.LinkedBlockingQueue;

/**
 * The RequestQueue takes a RequestMessage from a IServiceListener and puts it onto a queue
 *
 * The producer of this queue is a IServiceListener implementor and the consumer of this queue is a ServiceHandler.
 * The RequestQueue uses a thread-safe queue since it is possible for multiple consumers to be pushing messages onto it
 * and multiple producers taking messages from it.
 */
public class RequestQueue {
    public LinkedBlockingQueue<RequestMessage> queue;

    public RequestQueue() {
        this.queue = new LinkedBlockingQueue<>();
    }
}
