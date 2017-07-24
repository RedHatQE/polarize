package com.github.redhatqe.polarize.messagebus;

import com.fasterxml.jackson.databind.node.ObjectNode;

@FunctionalInterface
public interface MessageHandler {
    MessageResult handle(ObjectNode node);
}
