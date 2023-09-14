(ns monkey.socket-async.uds
  "Some wrapper functions for Unix domain sockets (UDS)"
  (:import [java.nio.channels SocketChannel ServerSocketChannel]
           [java.net UnixDomainSocketAddress StandardProtocolFamily]))

(def unix-proto StandardProtocolFamily/UNIX)

(defn make-address [path]
  (UnixDomainSocketAddress/of path))

(defn delete-address [path]
  (.delete (java.io.File. path)))

(defn connect-socket [addr]
  (SocketChannel/open addr))

(defn open-socket [addr]
  (.. (SocketChannel/open unix-proto)
      (bind addr)))

(defn listen-socket [addr]
  (.. (ServerSocketChannel/open unix-proto)
      (bind addr)))

(def accept (memfn ^ServerSocketChannel accept))

(def connected? (memfn ^SocketChannel isConnected))

(defn close [s]
  (when s
    (.close s)))
