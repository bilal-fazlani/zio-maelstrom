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
    Outgoing message can also extend for input parent trait if they also need to be received by the node. They just need to derive `JsonDecoder` additionally in that case.

## I/O APIs

### 1. `receive`

`receive` api takes a handler function `I => ZIO[MaelstromRuntime & R, Nothing, Unit]`

!!! note
    1. `I` needs have a `zio.json.JsonDecoder` instance
    2. `R` can be anything. You will need to provide `R` & `MaelstromRuntime` when you run the ZIO effect

Here's an example

<!--codeinclude-->
[Receive](../../examples/echo/src/main/scala/com/example/IODocs.scala) inside_block:Receive
<!--/codeinclude-->

1. `src` is the `NodeId` of the node that sent the message
2. `me` is the `NodeId` of the node that received the message
3. `others` is a list of `NodeId` received in the init message at the start of node

`receive` is a context function and it it gives some variables in the context of the handler function. i.e. `me`, `others` and `src`

### 2. `send`

You can send a message to any `NodeId` using `NodeId.send()` API. It takes a `Sendable` message which has a `zio.json.JsonEncoder` instance.

<!--codeinclude-->
[Send](../../examples/echo/src/main/scala/com/example/IODocs.scala) inside_block:Send
<!--/codeinclude-->
 
1. these will be sent to all nodes in cluster

### 3. `reply`

From within `receive` function, you can call `reply` api to send a reply message to the source of the current message.

<!--codeinclude-->
[Reply](../../examples/echo/src/main/scala/com/example/IODocs.scala) inside_block:Reply
<!--/codeinclude-->

`reply` api takes an instance of `Sendable` & `Reply` message which has a `zio.json.JsonEncoder` instance. 

!!! tip
    `reply` can be called only inside of receive function. Outside of the `receive` function, you can use `send` api which takes a remote `NodeId` argument.

### 4. `ask`

`ask` api is a combination of `send` and `receive`. It sends a message to a remote node and waits for a reply. It takes a `Sendable` & `Receive` message and returns a `Reply` message. It also takes a timeout argument which is the maximum time to wait for a reply. It expects a `zio.json.JsonDecoder` instance for the reply & a `zio.json.JsonEncoder` instance for the request message. `ask` api can be called from within and outside of `receive` function.

<!--codeinclude-->
[Ask](../../examples/echo/src/main/scala/com/example/IODocs.scala) inside_block:Ask
<!--/codeinclude-->

The `ask` api can return either a successful response or an `AskError`.

<!--codeinclude-->
[AskError](../../zio-maelstrom/src/main/scala/com/bilal-fazlani/zio-maelstrom/MessageSender.scala) inside_block:ask_error
<!--/codeinclude-->

Ask error can be one of the following: 

1. `Timeout` if the reply was not received within given duration
2. `DecodingFailure` if the reply could not be decoded into the given type
3. `ErrorMessage` if the sender sends an error message instead instead of the reply message. 
   
<!--codeinclude-->
[Ask error handling](../../examples/echo/src/main/scala/com/example/ErrorDocs.scala) inside_block:GetErrorMessage
<!--/codeinclude-->

Sender can send an error message if it encounters an error while processing the request message or when request is invalid. You can read more about error messages in the [next section](#error-messages).

## Error messages

zio-maelstrom has a built in data type for error messages called `ErrorMessage`

<!--codeinclude-->
[ErrorMessage](../../zio-maelstrom/src/main/scala/com/bilal-fazlani/zio-maelstrom/Protocol.scala) inside_block:errorMessage
<!--/codeinclude-->

It supports all the [standard maelstrom error codes](https://github.com/jepsen-io/maelstrom/blob/main/doc/protocol.md#errors) as well as ability to send custom error codes

??? note "View all error codes"
    <!--codeinclude-->
    [Error codes](../../zio-maelstrom/src/main/scala/com/bilal-fazlani/zio-maelstrom/Protocol.scala) inside_block:errorCodes
    <!--/codeinclude-->

You can send an error message to any node id as a reply to another message. Here's an example

<!--codeinclude-->
[Send standard error](../../examples/echo/src/main/scala/com/example/ErrorDocs.scala) inside_block:ReplyStandardError
<!--/codeinclude-->

1. You can set any text in `text` field

<!--codeinclude-->
[Send custom error](../../examples/echo/src/main/scala/com/example/ErrorDocs.scala) inside_block:ReplyCustomError
<!--/codeinclude-->


## Settings

## Logging

## Testing

### With Files

### With TestRuntime