package com.github.redhatqe.polarize.messagebus;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.redhatqe.byzantine.utils.Tuple;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

/**
 * Created by stoner on 6/2/17.
 */
public interface IMessageListener {
    /**
     * This method really shouldn't be used.  The handler here does not have a robust way of knowing whether a message
     * was successfully retrieved or not
     *
     * @return MessageHandler lambda
     */
    static MessageHandler defaultHandler() {
        return (node) -> {
            MessageResult result = new MessageResult(node);
            return result;
        };
    }

    /**
     * Returns a Supplier usable for a CompletableFuture object
     *
     * Normally, this function will be run in a thread from the fork/join pool since this method will block in the
     * bl.waitForMessage.  However, this function doesn't actually _do_ anything, as it returns a Supplier.  The
     * thread it is running on will actually call the Supplier and thus block, however, the main thread from which
     * getCIMessage itself is called will continue as normal.
     *
     * @return ObjectNode that is the parsed message
     */
    static Supplier<Optional<ObjectNode>> getCIMessage(CIBusListener bl, String selector, String path) {
        return () -> {
            ObjectNode root = null;
            bl.logger.info(String.format("Using selector of %s", selector));
            Optional<Tuple<Connection, Message>> maybeConn = bl.waitForMessage(selector);
            if (!maybeConn.isPresent()) {
                bl.logger.error("No Connection object found");
                return Optional.empty();
            }

            Tuple<Connection, Message> tuple = maybeConn.get();
            Connection conn = tuple.first;
            Message msg = tuple.second;

            // FIXME:  Should I write an exception handler outside of this function?  Might be easier than trying to
            // deal with it here (for example a retry)
            try {
                conn.close();
                root = bl.parseMessage(msg);
            } catch (ExecutionException | InterruptedException | JMSException e) {
                e.printStackTrace();
            }
            if (root != null)
                return Optional.of(root);
            else
                return Optional.empty();
        };
    }

    MessageListener createListener(MessageParser parser);

    Optional<Tuple<Connection, Message>> waitForMessage(String selector);

    Optional<Connection> tapIntoMessageBus(String selector, MessageListener listener);

    ObjectNode parseMessage(Message msg) throws ExecutionException, InterruptedException, JMSException;
}
