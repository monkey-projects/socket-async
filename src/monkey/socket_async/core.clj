(ns monkey.socket-async.core
  "Links core.async channels to unix domain sockets, or socket channels
   in general."
  (:import [java.nio.channels AsynchronousCloseException]
           java.nio.ByteBuffer
           [java.io PipedInputStream PipedOutputStream])
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
      (catch AsynchronousCloseException _
        ;; Socket channel was closed while we were writing
        false))))

(defn socket->input-stream
  "Creates an input stream that will receive all bytes read from the socket channel."
  [s]
  (let [pis (PipedInputStream.)
        pos (PipedOutputStream. pis)
        reader (fn []
                 (let [bb (ByteBuffer/allocate 0x10000)]
                   (loop [n (.read s bb)]
                     (if (neg? n)
                       ;; Socket closed
                       (do
                         (.close pos)
                         (.close pis))
                       (do
                         (.flip bb)
                         (.write pos (.array bb) 0 n)
                         (recur (.read s bb)))))))
        ;; TODO Check if we can do this using core.async
        t (doto (Thread. reader)
            (.start))]
    pis))

(defn read-onto-channel
  "Reads EDN structures from the socket and puts them on the channel."
  [s ch]
  (let [is (socket->input-stream s)
        r (-> is
              (io/reader)
              (java.io.PushbackReader.))
        read #(edn/read {:eof ::eof} r)]
    (ca/go-loop [obj (read)]
      (if (= ::eof obj)
        ;; EOF reached, close the channel
        (ca/close! ch)
        (do
          (>! ch obj)
          (recur (read)))))))

(defn write-from-channel
  "Reads objects from the channel and writes them as EDN to the socket"
  [ch s]
  (ca/go-loop [obj (<! ch)]
    (when obj
      (if (write s obj)
        (recur (<! ch))
        ;; If write returns false, then the socket was closed
        (ca/close! ch)))))
