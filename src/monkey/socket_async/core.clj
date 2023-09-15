(ns monkey.socket-async.core
  "Links core.async channels to unix domain sockets, or socket channels
   in general."
  (:import [java.nio.channels AsynchronousCloseException ClosedChannelException]
           java.nio.ByteBuffer
           [java.io IOException PipedInputStream PipedOutputStream])
  (:require [clojure.core.async :as ca :refer [>! <!]]
            [clojure.edn :as edn]
            [clojure.java.io :as io]))

(defn write
  "Writes `obj` to the socket as an EDN string"
  [sock obj]
  ;; TODO Reuse buffer?
  (let [b (.getBytes (pr-str obj))
        bb (doto (ByteBuffer/allocate (count b))
             (.put b)
             (.flip))]
    (try
      (.write sock bb)
      true
      ;; Socket channel was closed while we were writing
      (catch AsynchronousCloseException _
        false)
      (catch ClosedChannelException _
        false))))

(defn socket->input-stream
  "Creates an input stream that will receive all bytes read from the socket channel.
   This uses a dedicated thread, so only useful if you don't have too many of these."
  [s & [ex-handler]]
  (let [ex-handler (or ex-handler (constantly false))
        pis (PipedInputStream.)
        pos (PipedOutputStream. pis)
        close-all (fn []
                    (->> [pis pos]
                         (map (memfn close))
                         (doall)))
        read (fn [bb]
               (try
                 (.read s bb)
                 (catch Exception ex
                   ;; If the exception handler returns false, treat it as an EOF
                   (if (ex-handler ex)
                     0
                     -1))))
        reader (fn []
                 (let [bb (ByteBuffer/allocate 0x10000)]
                   (loop [n (read bb)]
                     (if (neg? n)
                       ;; Socket closed
                       (close-all)
                       (do
                         (.flip bb)
                         (.write pos (.array bb) 0 n)
                         (recur (read bb)))))))
        ;; We could avoid the dedicated thread by selecting on the socket.
        ;; This would however be a lot more work and it's yagni for now.
        t (doto (Thread. reader)
            (.start))]
    pis))

(defn read-onto-channel
  "Reads EDN structures from the socket and puts them on the channel.
   `ch` is closed on EOF.  `nil` objects are ignored, since you can't
   put `nil` on a channel.
   If an exception is thrown while reading, it's passed to `ex-handler`,
   which should return `true` in case we should continue, or `false`
   if we should stop reading and close `ch`.  The default behaviour 
   for `ex-handler` is to treat it like an EOF."
  [s ch & [ex-handler]]
  (let [ex-handler (or ex-handler (constantly false))
        is (socket->input-stream s ex-handler)
        r (-> is
              (io/reader)
              (java.io.PushbackReader.))
        read (fn []
               (try
                 (edn/read {:eof ::eof} r)
                 (catch Exception ex
                   (when-not (ex-handler ex)
                     ::eof))))]
    (ca/go-loop [obj (read)]
      (if (= ::eof obj)
        ;; EOF reached, close the channel
        (ca/close! ch)
        (do
          (when obj
            (>! ch obj))
          (recur (read)))))))

(defn write-from-channel
  "Reads objects from the channel and writes them as EDN to the socket.
   The loop will stop when `ch` is closed, or when an attempt to write 
   to `s` fails because the socket has been closed."
  [ch s]
  (ca/go-loop [obj (<! ch)]
    (when obj
      (if (write s obj)
        (recur (<! ch))
        ;; If write returns false, then the socket was closed
        (ca/close! ch)))))
