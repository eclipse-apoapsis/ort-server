# ActiveMQ Artemis

This module provides an implementation of the transport abstraction layer based on [Apache ActiveMQ Artemis](https://activemq.apache.org/components/artemis/).

## Synopsis

The module allows message exchange via ActiveMQ message queues.
It assumes that the queues in use are already configured via an external mechanism.
Their names have to be provided in the configuration.

The messages to be processed are converted to JMS text messages.
The payload is serialized to JSON and transferred in the text body of the message.
Metadata from the message header is represented by JMS message properties.

To use this module, the `type` property in the transport configuration must be set to `activeMQ`.

## Configuration

The configuration for message senders and receivers is identical.
Both require the URI to the message broker server and the name of the address to send messages to or receive messages from.

The following fragment shows the general structure:

```
endpoint {
  sender/receiver: {
    type = "activeMQ"
    serverUri = "amqp://artemis-broker-url:61616"
    queueName = "my_message_queue"
  }
}
```
