---
icon: material/api
hide:
  - navigation
---

# API Reference

!!! tip
    All the functionality of zio-maelstrom is available via following import statement
    
    ```scala
    import com.bilalfazlani.zioMaelstrom.*
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

### 3. `ask`

`ask` api is a combination of `send` and `receive`. It sends a message to a remote node and waits for a reply. It takes a `Sendable` & `Receive` message and returns a `Reply` message. It also takes a timeout argument which is the maximum time to wait for a reply. It expects a `zio.json.JsonDecoder` instance for the reply & a `zio.json.JsonEncoder` instance for the request message. `ask` api can be called from within and outside of `receive` function.

<!--codeinclude-->
[Ask](../../examples/echo/src/main/scala/com/example/IODocs.scala) inside_block:Ask
<!--/codeinclude-->

1. `MessageId.next` gives next sequential message id

!!! tip
    Use `MessageId.next` to generate a new message id. It is a sequential id generator

!!! important danger
    Make sure to use different message ids for different messages. If you use the same message id for different messages, the receiver will not be able to map the response to the request    

The `ask` api can return either a successful response or an `AskError`

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

Sender can send an error message if it encounters an error while processing the request message or when request is invalid. You can read more about error messages in the [error messages section](#error-messages)

### 4. `reply`

From within `receive` function, you can call `reply` api to send a reply message to the source of the current message.

<!--codeinclude-->
[Reply](../../examples/echo/src/main/scala/com/example/IODocs.scala) inside_block:Reply
<!--/codeinclude-->

`reply` api takes an instance of `Sendable` & `Reply` message which has a `zio.json.JsonEncoder` instance. 

!!! tip
    `reply` can be called only inside of receive function. Outside of the `receive` function, you can use `send` api which takes a remote `NodeId` argument.

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

ZIO-Maelstrom provides `LinkKv`, `LwwKv`, `SeqKv` & `LinTso` clients to interact with these services. `SeqKv`, `LwwKv` & `LinKv` are all key value stores. They have the same api but different consistency guarantees.

### Native KV APIs

_Native apis are provided by the maelstrom services_

`read`

:   Takes a key and returns the value of the key. If the value does not exist, it returns `KeyDoesNotExist` error code.

    <!--codeinclude-->
    [](../../examples/echo/src/main/scala/com/example/KvStoreDocs.scala) inside_block:read
    <!--/codeinclude--> 

`write`

:   Takes a key and a value and writes the value against the key. If a value already exists against the key, it is overwritten.

    <!--codeinclude-->
    [](../../examples/echo/src/main/scala/com/example/KvStoreDocs.scala) inside_block:write
    <!--/codeinclude--> 

`cas`

:   CAS stands for `compare-and-swap`. It takes a key, a value and an expected value. It writes the value against the key only if the expected value matches the current value of the key. If the value is different, then it returns `PreconditionFailed` error code. If the key does not exist, it returns `KeyDoesNotExist` error code. If you set `createIfNotExists` to true, it will create the key if it does not exist.

    <!--codeinclude-->
    [](../../examples/echo/src/main/scala/com/example/KvStoreDocs.scala) inside_block:cas
    <!--/codeinclude--> 

    Above example will write `3` to `counter` only if the current value of `counter` is `1`. If the current value is different, it will return `PreconditionFailed` error code.

### High level KV APIs

_High level apis are built on top of native apis by combining multiple native apis and/or adding additional logic_

`readOption`
   
:   Takes a key and returns an `Option` of the value of the key. If the value does not exist, it returns `None`. Does not return `KeyDoesNotExist` error code.

    <!--codeinclude-->
    [](../../examples/echo/src/main/scala/com/example/KvStoreDocs.scala) inside_block:ReadOption
    <!--/codeinclude--> 

`writeIfNotExists`

:   Takes a key and a value and writes the value against the key only if the key does not exist. If the key already exists, it returns `PreconditionFailed` error code.

    <!--codeinclude-->
    [](../../examples/echo/src/main/scala/com/example/KvStoreDocs.scala) inside_block:WriteIfNotExists
    <!--/codeinclude-->

`update`

:   This is a high level api built on top of other apis. It takes a key, a function that takes the current value and returns a new value. It reads the current value of the key, applies the function and writes the new value against the key. If the value has changed in the meantime, it applies the function again and keeps trying until the value does not change. This is useful for implementing atomic operations like incrementing a value.

    <!--codeinclude-->
    [](../../examples/echo/src/main/scala/com/example/KvStoreDocs.scala) inside_block:update
    <!--/codeinclude-->

    The timeout value does not apply to entire operation but to each individual read, cas and write operation. So the total time taken by the operation can be more than the timeout value. Retries are only done when the value has changed in the meantime. And other error is returned immediately. This also applies to `updateZIO` api.

`updateZIO`

:   This is a high level api built on top of other apis. It takes a key, a function that takes the current value and returns a `ZIO` that returns a new value. It reads the current value of the key, applies the `ZIO` and writes the new value against the key. If the value has changed in the meantime, it applies the function again and keeps trying until the value does not change. This is very similar to `update` but the function can be a `ZIO` which can do some async operations. When retries happen, the `ZIO` is retried as well, so side effects should be avoided in this function.

    <!--codeinclude-->
    [](../../examples/echo/src/main/scala/com/example/KvStoreDocs.scala) inside_block:UpdateZIO
    <!--/codeinclude--> 

!!! warning "Important"
    - Because all these apis are built on top of `ask` api, they can return `AskError` which you may need to handle.  According to [maelstrom documentation](https://github.com/jepsen-io/maelstrom/blob/main/doc/workloads.md#rpc-cas), they can return `KeyDoesNotExist` or `PreconditionFailed` error codes.

    - In case of network partition or delay, all of the above apis can return `Timeout` error code.

    - When incorrect types are used to decode the response, they can return `DecodingFailure` error code.

!!! tip
    key and value of the key value store can be any type that has a `zio.json.JsonCodec` instance

### TSO APIs

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

**Using static input files:**

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

**Using a fake context:**

Context is combination of node's id and the list of other nodes in the cluster. Context is received in the init message (sent by maelstrom server) at the start of the node.

When testing a node without maelstrom, if you are using a static input file, you have to manually create the context by including an init message in the file as shown above. When using stdIn you have manually enter the same message in terminal.

You can hardcode a "Fake" context in your program and use it for testing. This will allow you to test your program without maelstrom. Here's an example:

<!--codeinclude-->
[Fake Context](../../examples/echo/src/main/scala/com/example/ContextDocs.scala) inside_block:ContextDocs
<!--/codeinclude-->

This program will not wait for any input for initialization. Output:

```
initialised with fake context: Context(node1,Set(node2, node3, node4))
```