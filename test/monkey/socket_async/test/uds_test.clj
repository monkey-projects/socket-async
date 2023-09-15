(ns monkey.socket-async.test.uds-test
  (:require [clojure.test :refer [deftest testing is]]
            [monkey.socket-async.uds :as sut]))

(deftest open-socket
  (testing "binds to existing socket address"
    (let [path "test.sock"
          addr (sut/make-address path)]
      (try
        (is (some? (sut/open-socket addr)))
        (finally
          (sut/delete-address path))))))
