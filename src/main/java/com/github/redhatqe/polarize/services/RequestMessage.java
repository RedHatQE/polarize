package com.github.redhatqe.polarize.services;

/**
 * A base type for message requests.  The IServiceListener will receive a message in some kind of JSON format, and the
 * implementing class will convert that to an appropriate RequestMessage type.
 *
 * Known subclasses: XUnitRequestMessage, TestCaseRequestMessage, ConfigRequestMessage
 *
 * {
 *     source: 192.168.1.1
 *     endpoint: xunit
 *     msgBody: "<testsuite>...</testsuite>"
 * }
 */
public class RequestMessage {
    String source;           // Where the request came from
    String endpoint;         // What service to respond to
    String msgBody;
    MessageType mtype;
}
