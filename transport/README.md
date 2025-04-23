# Transport

This document describes the transport abstraction layer used within the ORT server to be independent of the concrete environment in which the server is running.
This folder contains the *spi* module defining the basic Service Provider Interfaces of the transport abstraction layer.
Then there are modules providing concrete implementations of these interfaces.
The latter have their own documentation.
This document focuses on the *spi* module and the concepts it introduces.

## Purpose

In the ORT server, there are multiple components that need to communicate with each other.
For instance, when a new request to trigger an analysis run is received via the REST API, it has to be forwarded to the Orchestrator.
The Orchestrator then sends a message to the Analyzer worker to start the analysis of the affected repository.
The results produced during this analysis need then to be passed back to the Orchestrator.

The concrete mechanisms used for exchanging such messages depend on the environment in which a server instance is running:
An ORT server hosted on AWS may use different messaging services than one on Azure; and a local installation may look completely different.

To abstract away the differences of specific transport mechanisms, the abstraction layer declares a set of classes and interfaces that define a generic protocol to send or receive messages.
All ORT components use these interfaces exclusively to exchange messages and are therefore agnostic of the underlying infrastructure.
At runtime, a specific implementation - suitable to the current environment - is selected based on configuration properties.
This implementation wires the ORT components together using a platform-specific messaging mechanism.

## Service Provider Interfaces

This section describes the classes and interfaces defined by the *Service Provider Interfaces* (SPI) module and the underlying concepts.
Concrete transport implementations have to implement the interfaces defined here.

### Endpoints and Messages

Messages are always sent to specific ORT components that are then responsible for their processing.
Since the number of ORT components is finite, this is also the case for the potential message receivers or *endpoints*.
They can therefore be represented by a number of constants - the subclasses of the sealed [Endpoint](spi/src/main/kotlin/Endpoint.kt) class.
Each subclass defines some metadata about the endpoint that is evaluated when setting up the communication infrastructure.

An endpoint can process messages of a specific type.
If an endpoint handles multiple messages, they are organized in a hierarchy of sealed classes.
This makes it possible to define type-safe interfaces for sending and receiving messages without having to deal with component-specific protocols.
For instance, the message sender interface has a single method to send a message of the base type to the target endpoint, instead of multiple methods for the different use cases supported by the receiving component.

Messages are represented by the [Message](spi/src/main/kotlin/Message.kt) class.
It consists of

- a message header defining some metadata properties
- the actual message payload whose type is derived from the target endpoint.

In the message header, a map with properties to be evaluated by the transport implementation is contained.
The map is populated from the labels passed to the current ORT run; so it basically stems from the caller.
Using this mechanism, it is possible to customize the behavior of the transport for a specific run.
The concept is described in detail at [Support for Different Tool Versions](../website/docs/guides/different-tool-versions.adoc).

### Factories

In order to send or receive messages, the infrastructure for message exchange must have been properly set up.
This is done via the factory interfaces `MessageSenderFactory` and `MessageReceiverFactory`.
Both interfaces provide static `create` functions that can be used to create sender or receiver instances compatible with the current environment.
They work as follows:

- The target endpoint for sending or receiving messages has to be provided.
- From the configuration, the factory function looks up the transport implementation configured for this endpoint.
- The factory function uses a `ServiceLoader` to find available implementations.
  Each existing implementation is assigned a unique name which is matched against the name obtained from the configuration.
- The implementation determined this way is invoked to create the actual sender or receiver object.

The factory functions rely on the presence of certain configuration properties to determine the correct transport implementation.
In theory, each endpoint could be reached via a different transport implementation; therefore, the configuration is endpoint-specific.
The `Endpoint` classes define a prefix for configuration keys; the configuration for a specific endpoint is located under this key.
The general configuration looks as follows:

```
analyzer {
  receiver {
    type = "transportName"
    transportProperty = "some value"
  }
}

orchestrator {
  sender {
    type = "otherTransportName"
    otherProperty = "other value"
  }
}
```

This fragment shows an example configuration for the Analyzer component (which is configured as message receiver).
Here `analyzer` is the configuration prefix defined for the Analyzer endpoint.
Under `receiver` the receiver is configured.
The `type` property is the one inspected by the factories to obtain the name of the concrete transport implementation; it must always be present.
There can be further optional properties specific to the protocol, e.g. to define connection strings, message queue names, etc.

The Analyzer component sends messages to the Orchestrator and has therefore a configuration section for this component.
Its structure is analogous, but as it is used for sending messages, the transport implementation and its properties are listed below `sender`.

The following sections contain examples how to use this mechanism in practice.

### Sending Messages

In order to send a message to a specific endpoint, one has to obtain a [MessageSender](spi/src/main/kotlin/MessageSender.kt) from a [MessageSenderFactory](spi/src/main/kotlin/MessageSenderFactory.kt).
Based on the example configuration contained at [Factories](#factories), this fragment shows how a message to the Orchestrator can be sent:

``` kotlin
val payload = AnalyzeResult(42)
val header = MessageHeader(token = "1234567890", traceId = "dick.tracy")
val message = Message(header, payload)

val sender = MessageSenderFactory.createSender(OrchestratorEndpoint, config)
sender.send(message)
```

Message senders should be obtained once, probably at component startup, and can then be reused during the lifetime of the component.
Note that the interface is typesafe; you can only send messages to an endpoint that it can process.

### Receiving messages

A component that can handle messages should set up a corresponding receiver when it starts.
This is done via the [MessageReceiverFactory](spi/src/main/kotlin/MessageReceiverFactory.kt) interface and involves specifying a handler function or lambda that is invoked for the incoming messages.
The example fragment below shows how the initialization code of the Orchestrator might look like:

``` kotlin
// Message handler function
fun handler(message: Message<OrchestratorMessage>) {
    // Message handling code
}

// Install receiver
MessageReceiverFactory.createReceiver(OrchestratorEndpoint, config, ::handler)
```

The `createReceiver` call is blocking.
It enters the message loop, which will wait for new messages and dispatch them to the handler function.

## Testing support

To simplify testing of message exchange between ORT server components, this module exposes a test transport implementation as a [test fixture](https://docs.gradle.org/current/userguide/java_testing.html#sec:java_test_fixtures).
It can be enabled in the configuration of an endpoint like regular transport implementations using the name "testMessageTransport"; so a test class could create a special test configuration that refers to the testing transport.

The implementation consists of the two factory classes `MessageSenderFactoryForTesting` and `MessageReceiverFactoryForTesting`.
Both provide companion objects that can be used to interact with message senders and receivers in a controlled way:

- With `MessageSenderFactoryForTesting.expectMessage()`, it can be tested whether the code under test has sent a message to a specific endpoint; this message is returned and can be further inspected.
- `MessageReceiverFactoryForTesting.receive()` allows simulating an incoming message to an endpoint.
  The function passes the provided message to the `EndpointHandler` function used by the owning endpoint.

These test implementations allow an end-to-end test of an ORT server endpoint: from an incoming request to the response(s) sent to other endpoints.
