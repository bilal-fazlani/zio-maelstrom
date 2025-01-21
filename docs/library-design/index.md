---
icon: fontawesome/solid/cubes
hide:
  - toc
  - navigation
---

### Principles

ZIO-Maelstrom follows ZIO idiomatic practices and is functional in nature

**What does this library do?**

- [x] Abstracts STDIN and STDOUT and exposes a simple Network IO interface to send and receive messages
- [x] Handles `Init` message
- [x] Calls your message handlers concurrently when messages arrive
- [x] RPC messages - maintains correlation between requests and responses
- [x] Gives you traits - `Sendable`, `Reply` and `NeedsResponse` to help you define your own protocol

**What it doesn't do**

- [ ] Define messaging protocols
- [ ] Define message handlers
- [ ] Handle errors and timeouts
- [ ] Maintain node state

These are the things you need to do. Because you are using `ZIO`, you get super powers to deal with concurrency, domain errors, timeout errors, state management, retries and more. The possibilities are endless with `ZIO` ecosystem and primitives such as `Ref`, `Promise`, `Queue`, `STM` etc.

### Design

### The runtime

The runtime is implemented as `ZLayer`s. Because `ZLayer` allows creation from a `ZIO` effect, it is possible to run effects before message handlers are invoked

This is more evident if we look at the definition of type `MaelstromRuntime` and its layer construction

<!--codeinclude-->
[Maelstrom Runtime](../../zio-maelstrom/src/main/scala/com/bilalfazlani/zioMaelstrom/MaelstromRuntime.scala) inside_block:definition
<!--/codeinclude-->

<!--codeinclude-->
[Layer construction](../../zio-maelstrom/src/main/scala/com/bilalfazlani/zioMaelstrom/MaelstromRuntime.scala) inside_block:doc_incluide
<!--/codeinclude-->

Using effects to create layers makes these effects run before user's effect. In this case initialization and starting of response handler is done as part of layer creation

!!! note
    Initialization refers to the handling of `init` message. You can read more about it [here](https://github.com/jepsen-io/maelstrom/blob/main/doc/protocol.md#initialization)

```mermaid
graph BT

L0("OutputChannel.stdOut")

L7 --> L2("Logger.live")

L2 --> L3("inputStream")

L2 --> L5("RequestHandler.live")
L4 --> L5
L6 --> L5
L7 --> L5

L0 --> L4("Initialisation.run")
L8 --> L4
L2 --> L4

L0 --> L6("MessageSender.live")
L4 --> L6
L2 --> L6
L9 --> L6


L7("ZLayer.succeed(settings)")

L2 --> L8("InputChannel.live")
L3 --> L8

L2 --> L9("CallbackRegistry.live")

L10("ResponseHandler.start")
L4 --> L10
L9 --> L10
L7 --> L10
```

### Reading STDIN

Reading STDIN is done using a `ZStream` and then each line is parsed into a `GenericMessage`. `GenericMessage` is a semi parsed messaged that is used to determine the `type` of message, whether it is a reply to some other message, etc.

The first element of this stream is assumed to be `init` message because that is guaranteed by maelstrom. First element is consumed from the stream and then the remaining stream is split into two streams - one for the normal (request) messages and one for the [replies](#request-response-pattern). This is done using `ZStream#partition` method. The partitioning is done using the `in_reply_to` field of the message. If this field is set, the message is identified as reply.

![Reading STDIN](stdin.svg#only-light) ![Stream Partition](stream-partition.svg#only-light)
![Reading STDIN](stdin-dark.svg#only-dark) ![Stream Partition](stream-partition-dark.svg#only-dark)

These two streams are subscribed by two different consumers. Request stream is consumed by the `receive` api in `RequestHandler` (invoked by the user). Reply stream is consumed by the `ResponseHandler` which is invoked during creation of `MaelstromRuntime` layer.

### Request-Response pattern

The `ask` api lets users send a message and also wait for a reply. It uses `Promise` to achieve this. When a message is sent, a `Promise` is created and stored in the `CallbackRegistry` layer in a map against a `CallbackId`. A callbackId is a combination of `msg_id` and `dest` of the message. When a reply is received, the `CallbackRegistry` layer completes the promise with the reply message. Callback registry decides which promise to complete based on `in_reply_to` and `src` fields of the reply message.

The `ask` api is resource-safe. Meaning if an `ask` operation is interrupted, the callback (promise) is removed the registry. Promises are also removed in the event of a timeout and when a reply is received. This is done using ZIO's Scope and its Finalizer.