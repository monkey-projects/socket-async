(ns monkey.socket-async.test.core-test
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.core.async :as ca :refer [go >! <!]]
            [monkey.socket-async
             [core :as sut]
             [uds :as uds]]))

(defn with-sockets
  "Opens listening socket and connects a client socket to it.
   Invokes `f` with a map that holds `:listener`, `:clietn` and
   `:server`."
  [f]
  (let [path (format "test=%s.sock" (random-uuid))
        addr (uds/make-address path)
        listener (uds/listen-socket addr)
        client (uds/connect-socket addr)
        server (uds/accept listener)]
    (is (uds/connected? client))
    (is (uds/connected? server))
    (try
      (f {:listener listener
          :client client
          :server server})
      (finally
        ;; Clean up
        (uds/close client)
        (uds/close server)
        (uds/delete-address path)))))

(defn- read-or-timeout [ch & [timeout]]
  (let [t (ca/timeout (or timeout 1000))
        [v p] (ca/alts!! [ch t])]
    (if (= ch p)
      v
      :timeout)))

(deftest read-and-write
  (testing "can send objects between channels using socket"
    (with-sockets
      (fn [{:keys [client server]}]
        (let [obj {:message "Hi, this is a test"}
              in (ca/chan)
              out (ca/chan)]
          (is (some? (sut/read-onto-channel server in)))
          (is (some? (sut/write-from-channel out client)))
          (is (true? (ca/>!! out obj)))
          (let [v (read-or-timeout in)]
            (is (not= :timeout v) "did not expect timeout")
            (is (= obj v)) "did not receive the object sent")
          (ca/close! in)
          (ca/close! out)))))

  (testing "stops reading when connection closes"
    (with-sockets
      (fn [{:keys [client server]}]
        (let [in (ca/chan)
              r (sut/read-onto-channel server in)]
          (is (some? r))
          (uds/close client)
          (is (nil? (read-or-timeout r)))))))

  (testing "stops writing when connection closes"
    (with-sockets
      (fn [{:keys [client server]}]
        (let [out (ca/chan)
              r (sut/write-from-channel out client)]
          (is (some? r))
          (uds/close client)
          (go (>! out {:key "test value"}))
          (is (nil? (read-or-timeout r))))))))
