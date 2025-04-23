# SQS Transport Implementation

This module provides an implementation of the transport abstraction layer based on [AWS SQS](https://aws.amazon.com/sqs/).
Any compatible implementations should be supported as well, including [LocalStack](https://www.localstack.cloud/) and [Scaleway](https://www.scaleway.com/en/developers/api/messaging-and-queuing/sqs-api/).

## Synopsis

The module allows message exchange via SQS message queues.
It assumes that the queues in use are already configured via an external mechanism.
Their names have to be provided in the configuration.

The messages to be processed are converted to send message requests.
Metadata from the message header is represented by message attributes values.
The payload is serialized to JSON and transferred in the text body of the message.

To use this module, the `type` property in the transport configuration must be set to `SQS`.

## Configuration

The configuration for message senders and receivers is identical.
Both require the URI to the server endpoint to send messages to or receive messages from.

The following fragment shows the general structure:

```
endpoint {
  sender/receiver: {
    type = "SQS"
    serverUri = "https://sqs.hostname.com:32803/"
    accessKeyId = "my-access-key-id"
    secretAccessKey = "my-secret-access-key"
    queueName = "my-message-queue"
  }
}
```
