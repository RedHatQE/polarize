package com.github.redhatqe.polarize.messagebus;


import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Optional;

public class MessageResult {
    private ObjectNode node;
    private Status status;
    public String errorDetails = "";

    public MessageResult() {
        this.status = Status.PENDING;
        this.node = null;
    }

    public MessageResult(ObjectNode node) {
        this.node = node;
        this.status = (node == null) ? Status.NO_MESSAGE : Status.SUCCESS;
    }

    public MessageResult(ObjectNode node, Status status) {
        this.node = node;
        this.status = status;
    }

    public Optional<ObjectNode> getNode() {
        return Optional.ofNullable(this.node);
    }

    public Status getStatus() { return this.status; }

    public void setNode(ObjectNode node) {
        this.node = node;
    }

    public void setStatus(Status stat) {
        this.status = stat;
    }

    public enum Status {
        FAILED,
        SUCCESS,
        PENDING,
        NO_MESSAGE,
        EMPTY_MESSAGE,
        TIMED_OUT,
        NP_EXCEPTION,
        JMS_EXCEPTION;
    }
}
