---
icon: material/api
hide:
  - navigation
---

# API Reference

!!! tip
    All the functionality of zio-maelstrom is available via two import statements
    
    ```scala
    import com.bilalfazlani.zioMaelstrom.*
    import com.bilalfazlani.zioMaelstrom.protocol.*
    ```

## Primitive Types

### `NodeId`

NodeId represents a unique identifier for a node. It is a wrapper (opaque type) around `String` and can be created using `NodeId(String)` method.

### `MessageId`

MessageId represents a unique identifier for a message. It is a wrapper (opaque type) around `Int` and can be created using `MessageId(Int)` method.

## Protocol

In order to send and receive messages from a node, we need to create data types that extend from one or more of the following traits:

### 1. `Sendable`

If a message needs to be "sent" out of a node, it needs to extend from `Sendable`

```scala
trait Sendable:
  val `type`: String
```

`type` identifies the type of message which helps in decoding and handling of the message.  

### 2. `Reply`
   
If we want to "reply" against another message to a node, the reply data class needs to extend from `Reply` trait

```scala
trait Reply:
  val in_reply_to: MessageId
```

`in_reply_to` is the `MessageId` of the message that we are replying against. Most of the time, you will have to extend reply messages from `Sendable` as well because replies need to be "sent" out of a node.

### 3. `NeedsReply`
   
If we want to "receive" a reply against a message, the message data class needs to extend from `NeedsReply` trait

```scala
trait NeedsReply:
  val msg_id: MessageId
```

This is required to map response messages to request message using the `msg_id` field.

## Json SerDe

Besides the traits, any message that needs to be sent as a request to another node or as a response for another message, should have a `zio.json.JsonEncoder` instance. This is required to encode the message into a JSON string which is then sent to the node. Likewise, any message that needs to be received as a request from another node or as a response for another message, should have a `zio.json.JsonDecoder` instance. This is required to decode the JSON string into the message.

In the unique-ids example, we have defined the following messages:

<!--codeinclude-->
[Message definitions](../../examples/unique-ids/src/main/scala/com/example/Main.scala) inside_block:messages
<!--/codeinclude-->

Here, `Generate` message extends from `NeedsReply` because it expects a reply message. `Generate` message is sent by maelstrom server nodes and not the application nodes. Application nodes just receive the message. Hence it does not need to extend from `Sendable`. `GenerateOk` message is the response for `Generate` and because application node needs to send it, it needs to extend from both `Sendable` and `Reply`.

If a message needs to be sent as well as received, it needs an instance of `zio.json.JsonCodec` which is a combination of `zio.json.JsonEncoder` and `zio.json.JsonDecoder`. 

Usually a node wants to handle more than one type of message. For that, we discriminate messages using the `type` field using `jsonDiscriminator` annotation of zio-json. Here's an example

<!--codeinclude-->
[Input message definitions](../../examples/echo/src/main/scala/com/example/Calculator.scala) inside_block:in_messages
<!--/codeinclude-->

Since, the parent trait is deriving a `JsonDecoder`, we don't need to derive it for individual messages. 

However, we need to derive `JsonEncoder` for each outgoing message because there is usually no parent type for outgoing messages.

<!--codeinclude-->
[Output message definitions](../../examples/echo/src/main/scala/com/example/Calculator.scala) inside_block:out_messages
<!--/codeinclude-->

!!! note
    Outgoing message can also extend for input parent trait if they also need to be received by the node. They just need to derive `JsonDecoder` in that case.

## I/O

### receive

### ask

### error messages

## Settings

## Logging

## Testing

### With Files

### With TestRuntime