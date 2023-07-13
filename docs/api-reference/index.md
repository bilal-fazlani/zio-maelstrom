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
3. `ErrorMessage` if the sender sends an error message instead of the reply message. 
   
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

## Maelstrom services

Maelstrom starts some services at the beginning of every simulation by default

These are their node ids:

1. `lin-kv`
2. `lww-kv`
3. `seq-kv`
4. `lin-tso`

You can read more these services on the [maelstrom docs](https://github.com/jepsen-io/maelstrom/blob/main/doc/services.md)

ZIO-Maelstrom provides `LinkKv`, `LwwKv`, `SeqKv` & `LinTso` clients to interact with these services. 

<!--codeinclude-->
[Key value store](../../examples/echo/src/main/scala/com/example/KvStoreDocs.scala) inside_block:SeqKvExample
<!--/codeinclude--> 

`SeqKv`, `LwwKv` & `LinKv` are all key value stores. They have the same api but different consistency guarantees.

!!! note
    `read`, `write` and `cas` apis are all built on top of [`ask`](#4-ask) api. So they can return an `AskError` which you may need to handle. According to [maelstrom documentation](https://github.com/jepsen-io/maelstrom/blob/main/doc/workloads.md#rpc-cas), they can return `KeyDoesNotExist` or `PreconditionFailed` error codes.

!!! tip
    key and value of the key value store can be any type that has a `zio.json.JsonCodec` instance

[`LinTso`](https://github.com/jepsen-io/maelstrom/blob/main/doc/services.md#lin-tso) is a linearizable timestamp oracle. It has the following api

<!--codeinclude-->
[Linearizable timestamp oracle](../../examples/echo/src/main/scala/com/example/TsoDocs.scala) inside_block:TsoExample
<!--/codeinclude-->

## Settings

Below are the settings that can be configured for a node

1. **NodeLogLevel**

    ??? note "View all log levels"
        <!--codeinclude-->
        [NodeLogLevel](../../zio-maelstrom/src/main/scala/com/bilal-fazlani/zio-maelstrom/Settings.scala) inside_block:log_levels
        <!--/codeinclude-->

    The default log level is `NodeLogLevel.Info`. If you want more detailed logs, you can set it to `NodeLogLevel.Debug`. If you want to disable logs, you can set it to `NodeLogLevel.Off`

2. **LogFormat**

    Log format can be either Plain or Colored. Default is colored.

3. **Concurrency**

    This is the concurrency level for processing messages. Default is 1024. This means 1024 request messages(receive api) + 1024 response messages (ask api) = 2048 messages can be processed in parallel.

<!--codeinclude-->
[Default](../../examples/echo/src/main/scala/com/example/SettingsDocs.scala) inside_block:DefaultSettingsDocs
<!--/codeinclude-->

<!--codeinclude-->
[Custom](../../examples/echo/src/main/scala/com/example/SettingsDocs.scala) inside_block:CustomSettingsDocs
<!--/codeinclude-->

## Logging

You can log at different levels using below APIs:

```scala
def logDebug(message: => String)
def logInfo(message: => String)
def logWarn(message: => String)
def logError(message: => String)
```

All these APIs log to STDERR because STDOUT is used for sending messages. You can configure the log level using `NodeLogLevel` setting. By default LogFormat is colored. You can change it to plain using `LogFormat` setting.

<!--codeinclude-->
[Logging](../../examples/echo/src/main/scala/com/example/LogDocs.scala) inside_block:MainApplication
<!--/codeinclude-->

Above program, when initialized, will output the following:

![log-output](logs.png)

## Testing

When developing a solution, you sometimes want to test it without maelstrom. And manually entering the same inputs every time can be time consuming. You can configure the runtime to read the input from a file.

<!--codeinclude-->
[With Files](../../examples/echo/src/main/scala/com/example/FileInputDocs.scala) block:Main
<!--/codeinclude-->

<!--codeinclude-->
[fileinput.txt](../../examples/echo/fileinput.txt)
<!--/codeinclude-->

This will run the entire program with the input from the file. With file input you also get to simulate delay in inputs using sleep statements as shown above.

![file-input](fileinput.png)

!!! tip
    When debugging an issue, you can use file inputs, set log level to debug and set concurrency to 1. This might help you isolate the issue.
