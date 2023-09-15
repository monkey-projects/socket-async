# Socket Async

This is a small Clojure lib that provides functionality to links
[Java SocketChannels](https://docs.oracle.com/en/java/javase/20/docs/api/java.base/java/nio/channels/SocketChannel.html) to Clojure's [core.async channels](https://clojure.github.io/core.async/).

## Why?

Although it seems like something fairly straightforward and basic, I could not find
anything like it.  I just needed something that allows you to write Clojure structures
that can be serialized (into `edn` for example) to a channel, and then can be read
back on the other end.  With plain `core.async` channels this is easy.  But for some
IPC purposes, it's handy to stream this over a socket channel (like a Unix domain
socket, aka UDS).

It's more complicated than it seems, especially since Java does not provide an
out-of-the-box way to read from a socket channel to an `InputStream`, unless you're
using network channels.  So it was not possible with UDS.

## Usage

Include it in your project:
```clojure
# deps.edn
{:deps {com.monkeyprojects/socket-async {:mvn/version "..latest.."}}}

# Or project.clj
[com.monkeyprojects/socket-asybnc "..latest.."]
```

The core functionality is in `monkey.socket-async.core`.  There are also some
helper functions for UDS in `monkey.socket-async.uds`, but you could also directly
use the [Java classes](https://docs.oracle.com/en/java/javase/20/docs/api/java.base/java/nio/channels/package-summary.html).  You also need `core.async` channels, of course.

The two "work horses" are `read-onto-channel` and `write-from-channel`.  The former
reads `EDN` from a `SocketChannel` and puts the decoded objects onto a channel.
The latter does the opposite, it reads objects from a channel, encodes them as `EDN`
and writes that string to the socket channel.  For example:

```clojure
(require '[monkey.socket-async.core :as c])
(require '[monkey.socket-async.uds :as uds])

;; Set up socket connection
(def path "test.sock")
(def addr (uds/make-address path))
(def listener (uds/listen-socket addr)) ; Start listening
(def client (uds/connect-socket addr))  ; Connect to the server
(def server (uds/accept listener) ; Accept incoming connection

;; At this point you have two socket channels, client and server
;; that are connected to each other.

(require '[clojure.core.async :as ca :refer [go >!]])

(def in (ca/chan))
(def out (ca/chan))

;; Connect the channels to the sockets, in one direction in this example
(c/read-onto-channel server in)
(c/write-from-channel out client)

;; Send something
(go (>! out {:message "Hi, this is a test"}))

;; Receive it.  Set a timeout for safety.
(ca/alts!! [in (ca/timeout 1000)])
;; This should return the message we just wrote
```

The `read-onto-channel` function also accepts an additional exception handler fn,
that gets called when an IO exception occurs.  This typically means the connection
was closed.  The handler should return `false` if we should not continue processing
further messages.  This is the default behaviour.  But you could provide your own,
for example to do error reporting or logging, or handle certain edge cases.

## TODO

Currently this only supports `EDN`, but it could be expanded so that you can configure
the serialization method (e.g. use `JSON` instead).

## License

Copyright (c) 2023 by Monkey Projects

[MIT license](LICENSE)