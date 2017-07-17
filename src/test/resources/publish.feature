Feature: Publish a message to a JMS Message Broker

  A JMS Message encoded as a JSON object should be submitted to a message broker.  This message must define at a minimum
  a JMS type and have a non-empty body.

  Scenario Outline: Send a JSON message to message broker
    Given a JSON message "body" with the following
      """
      { "testing": "Hello world" }
      """
    And in the header there is a <key> with a value of <val>
    And the default config file is used
    When the JMS selector is set to <sel-key>='<sel-val>'
    And the message is sent to the <broker> url
    Then the message should be received with the reply body of
      """
      { "testing": "Hello world" }
      """
    And the message result status should be "SUCCESS"
    Examples:
      | broker     | key     | val          | sel-key | sel-val      |
      | ci         | rhsm_qe | polarize_bus | rhsm_qe | polarize_bus |