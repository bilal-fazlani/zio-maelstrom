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

In order to send and receive messages from a node, we need to create case classes that contain the fields which we need to send or receive. 
The case classes dont need to contain standard fields defined in [maelstrom protocol](https://github.com/jepsen-io/maelstrom/blob/main/doc/protocol.md) like `src`, `dest`, `type`, `msg_id`, `in_reply_to`, etc. Those are automatically added and parsed by the runtime.

=== "Message on the wire"

    ```scala
    case class Echo(text: String) derives JsonCodec
    ```

    ```json
    {
        "src" : "c1", // (1)!
        "dest" : "n1", // (2)!
        "body" : {
            "type": "echo", // (3)!
            "msg_id": 10, // (4)!
            "in_reply_to": 5, // (5)!
            "text": "Hello" // (6)!
        }
    }
    ```

    1.  `src` is the `NodeId` of the node that sent the message
    2.  `dest` is the `NodeId` of the node that received the message
    3.  `type` is the type of the message (snake case version of the case class name)
    4.  `msg_id` is automatically added when `ask` api is used
    5.  `in_reply_to` is automatically added when `reply` api is used
    6.  These are the fields of the case class (in this case, `text`). Fields can be zero or multiple and nested as well.

=== "Send serialization"

    ```scala
    val echo = Echo("Hello")
    val nodeId = NodeId("n1")
    nodeId.send(echo)
    ```

    ```json
    {
        "src" : "c1"
        "dest" : "n1",
        "body" : {
            "type": "echo",
            "text": "Hello"
        }
    }
    ```

=== "Ask serialization"

    ```scala
    val echo = Echo("Hello")
    val nodeId = NodeId("n1")
    nodeId.ask[Echo](echo, 5.seconds)
    ```

    ```json
    {
        "src" : "c1",
        "dest" : "n1",
        "body" : {
            "type": "echo",
            "text": "Hello",
            "msg_id": 10
        }
    }
    ```

=== "Reply serialization"

    ```scala
    val echo = Echo("Hello")
    reply(echo, 3.seconds)
    ```

    ```json
    {
        "src" : "n1",
        "dest" : "c1",
        "body" : {
            "type": "echo",
            "text": "Hello",
            "in_reply_to": 10
        }
    }
    ```


!!! note
    Keep in mind that if you use `ask` api, framework adds a `msg_id` to the message. If you use `reply` api, framework adds a `in_reply_to` to the message. 
    
!!! warning "Caution" 
    If you try to use `reply` api for a message that does not have a `msg_id` (i.e. sent using `send` api), it will throw an error at runtime.

!!! warning "Caution" 
    If you use `ask` api, the called will wait for the reply with a timeout. If the reply is not received within the timeout, it will return `Timeout` error.

The idea behind framework design is that when writing solutions to problems, should should not have to think about msg_id and other things. Much like when we write an HTTP/GRPC client or server. 

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


### 2. `send`

You can send a message to any `NodeId` using `NodeId.send()` API. It takes a instance of a case class which has a `zio.json.JsonEncoder`.

<!--codeinclude-->
[Send](../../examples/echo/src/main/scala/com/example/IODocs.scala) inside_block:Send
<!--/codeinclude-->
 
1. these will be sent to all nodes in cluster

### 3. `ask`

`ask` api is a combination of `send` and `receive`. It sends a message to a remote node and waits for a reply. It also takes a timeout argument which is the maximum time to wait for a reply. It expects a `zio.json.JsonDecoder` instance for the reply & a `zio.json.JsonEncoder` instance for the request message.

<!--codeinclude-->
[Ask](../../examples/echo/src/main/scala/com/example/IODocs.scala) inside_block:Ask
<!--/codeinclude-->

The `ask` api can return either a successful response or an `AskError`

<!--codeinclude-->
[AskError](../../zio-maelstrom/src/main/scala/com/bilalfazlani/zioMaelstrom/MessageSender.scala) inside_block:ask_error
<!--/codeinclude-->

Ask error can be one of the following: 

1. `Timeout` if the reply was not received within given duration
2. `DecodingFailure` if the reply could not be decoded into the given type
3. `Error` if the sender sends an error message instead of the reply message. 
   
<!--codeinclude-->
[Ask error handling](../../examples/echo/src/main/scala/com/example/ErrorDocs.scala) inside_block:GetErrorMessage
<!--/codeinclude-->

Sender can send an error message if it encounters an error while processing the request message or when request is invalid. You can read more about error messages in the [error messages section](#error-messages)

### 4. `reply`

You can call `reply` api to send a reply message to the source of the current message (if the message was sent using `ask` api)

<!--codeinclude-->
[Reply](../../examples/echo/src/main/scala/com/example/IODocs.scala) inside_block:Reply
<!--/codeinclude-->

`reply` api takes an instance of a case class which has a `zio.json.JsonEncoder`   

## Error messages

zio-maelstrom has a built in data type for error messages called `Error`

<!--codeinclude-->
[Error](../../zio-maelstrom/src/main/scala/com/bilalfazlani/zioMaelstrom/Protocol.scala) inside_block:errorMessage
<!--/codeinclude-->

It supports all the [standard maelstrom error codes](https://github.com/jepsen-io/maelstrom/blob/main/doc/protocol.md#errors) as well as ability to send custom error codes

??? note "View all error codes"
    <!--codeinclude-->
    [Error codes](../../zio-maelstrom/src/main/scala/com/bilalfazlani/zioMaelstrom/Protocol.scala) inside_block:errorCodes
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

:   This is a high level api built on top of other apis. It takes a key, a function that takes the current value and returns a `ZIO` that returns a new value. It reads the current value of the key, applies the `ZIO` and writes the new value against the key. If the value has changed in the meantime, it applies the function again and keeps trying until the value does not change. This is very similar to `update` but the function can be a `ZIO` which can do some async operations. 

    <!--codeinclude-->
    [](../../examples/echo/src/main/scala/com/example/KvStoreDocs.scala) inside_block:UpdateZIO
    <!--/codeinclude--> 

!!! danger "Danger"
    When retries happen, the `ZIO` is retried as well, so side effects should be avoided in this function.

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

1. **Log Level**
    
    The default log level is `LogLevel.Info`. 
    If you want more detailed logs, you can set it to `LogLevel.Debug`. 
    If you want to disable logs, you can set it to `LogLevel.None`

2. **Log Format**

    Log format can be either Plain or Colored. Default is colored.

3. **Concurrency**

    This is the concurrency level for processing messages. Default is 1024. 
    This means 1024 request messages(receive api) + 1024 response messages (ask api) = 2048 messages can be processed in parallel.

<!--codeinclude-->
[Default example](../../examples/echo/src/main/scala/com/example/SettingsDocs.scala) inside_block:DefaultSettingsDocs
<!--/codeinclude-->

<!--codeinclude-->
[Customization example](../../examples/echo/src/main/scala/com/example/SettingsDocs.scala) inside_block:CustomSettingsDocs
<!--/codeinclude-->

## Logging

You can log at different levels using ZIO's logging APIs - `ZIO.logDebug`, `ZIO.logInfo`, etc.
All these APIs log to STDERR because STDOUT is used for sending messages.
You can configure the log level using [settings](#settings) API.
By default, log statements are colored. You can change it to plain using [settings](#settings) API

<!--codeinclude-->
[Logging](../../examples/echo/src/main/scala/com/example/LogDocs.scala) block:MainApplication
<!--/codeinclude-->

Above program, when initialized, will output the following:

![log-output](logs.png)

## Testing

**Using static inline messages:**

When developing a solution, you sometimes want to test it without maelstrom. While you can use `stdIn` to enter the input, you can also hardcode the input messages in the program itself.

<!--codeinclude-->
[Inline Input Messages](../../examples/echo/src/main/scala/com/example/InlineInput.scala) block:InlineInput
<!--/codeinclude-->

1. When using inline input, you need JsonEncoder and JsonDecoder instances for input messages
2. Node's own id
3. Other nodes in the cluster
4. Messages to be sent to the node. These are varargs and you can send any number of messages

!!! tip
    When debugging an issue, use static input, set log level to debug and set concurrency to 1. This might help you isolate the issue.

**Using static input files:**

You can configure the runtime to read the input from a file.

<!--codeinclude-->
[With Files](../../examples/echo/src/main/scala/com/example/FileInputDocs.scala) block:Main
<!--/codeinclude-->

1. Node's own id
2. Other nodes in the cluster
3. Path to the file containing the input messages

<!--codeinclude-->
[fileinput.txt](../../examples/echo/fileinput.txt)
<!--/codeinclude-->

This will run the entire program with the input from the file. With file input you also get to simulate delay in inputs using sleep statements as shown above.

![file-input](fileinput.png)