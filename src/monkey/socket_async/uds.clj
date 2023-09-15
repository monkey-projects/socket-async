(ns monkey.socket-async.uds
  "Some wrapper functions for Unix domain sockets (UDS)"
  (:import [java.nio.channels SocketChannel ServerSocketChannel]
           [java.net UnixDomainSocketAddress StandardProtocolFamily]))

(def unix-proto StandardProtocolFamily/UNIX)

(defn make-address
  "Creates a UDS address at given path.  The file must not exist yet."
  [path]
  (UnixDomainSocketAddress/of path))

(defn delete-address
  "Deletes the socket file at `path`."
  [path]
  (.delete (java.io.File. path)))

(defn connect-socket
  "Opens a client socket on `addr`"
  [addr]
  (SocketChannel/open addr))

(defn open-socket
  "Creates a client socket that binds to `addr`."
  [addr]
  (.. (SocketChannel/open unix-proto)
      (bind addr)))

(defn listen-socket
  "Creates a listening socket that binds to `addr`"
  [addr]
  (.. (ServerSocketChannel/open unix-proto)
      (bind addr)))

(def accept (memfn ^ServerSocketChannel accept))

(def connected? (memfn ^SocketChannel isConnected))

(defn close
  "Closes the socket channel, if non `nil`"
  [s]
  (when s
    (.close s)))
