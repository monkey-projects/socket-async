(ns monkey.socket-async.test.core-test
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.core.async :as ca :refer [go >! <!]]
            [monkey.socket-async
             [core :as sut]
             [uds :as uds]]))

(deftest read-and-write
  (testing "can send objects between channels using socket"
    (let [path "test.sock"
          addr (uds/make-address path)
          listener (uds/listen-socket addr)
          client (uds/connect-socket addr)
          server (uds/accept listener)]
      (is (uds/connected? client))
      (is (uds/connected? server))
      (try
        (let [obj {:message "Hi, this is a test"}
              in (ca/chan)
              out (ca/chan)]
          (is (some? (sut/read-onto-channel server in)))
          (is (some? (sut/write-from-channel out client)))
          (is (true? (ca/>!! out obj)))
          (let [t (ca/timeout 1000)
                [v p] (ca/alts!! [in t])]
            (is (= in p) "did not expect timeout")
            (is (= obj v)) "did not receive the object sent")
          (ca/close! in)
          (ca/close! out))
        (finally
          ;; Clean up
          (uds/close client)
          (uds/close server)
          (uds/delete-address path))))))
