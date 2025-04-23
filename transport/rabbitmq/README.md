# RabbitMQ Transport implementation

This module provides an implementation of the transport abstraction layer based on [RabbitMQ](https://www.rabbitmq.com/).

## Synopsis

The module allows message exchange via RabbitMQ message queues.
It assumes that the queues in use are already configured via an external mechanism.
Their names have to be provided in the configuration.

The messages to be processed are converted to AMQP messages.
The payload is serialized to JSON and transferred in the text body of the message.
Metadata from the message header is represented by AMQP message properties.

To use this module, the `type` property in the transport configuration must be set to `rabbitMQ`.

## Configuration

The configuration for message senders and receivers is identical.
Both require the URI and the credentials to the message broker server and the name of the involved message queue.
The credentials are obtained as secrets from the [ConfigManager](../../config/README.md).

The following fragment shows the general structure:

```
endpoint {
  sender/receiver: {
    type = "rabbitMQ"
    serverUri = "amqps://rabbit-mq-server.com:5671"
    queueName = "my_message_queue"
    rabbitMqUser = "myUsername"
    rabbitMqPassword = "myPassword"
  }
}
```

This table contains a description of the supported configuration properties:

| Property         | Description                                                                     | Secret |
|------------------|---------------------------------------------------------------------------------|--------|
| serverUri        | The URI of the RabbitMQ server.                                                 | no     |
| queueName        | The name of the message queue to send messages to or to retrieve messages from. | no     |
| rabbitMqUser     | The username to authenticate against the RabbitMQ server.                       | yes    |
| rabbitMqPassword | The password to authenticate against the RabbitMQ server.                       | yes    |

> [!NOTE]
> It is possible to set configuration properties via environment variables.
> Since each endpoint has its own messaging configuration, different environment variables are used.
> Inspect the different `application.conf` files to find the variables in use.
