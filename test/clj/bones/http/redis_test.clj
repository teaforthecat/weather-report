(ns bones.http.redis-test
  (:require [clojure.test :refer [deftest testing is use-fixtures run-tests]]
            [bones.http.redis :as redis]
            [bones.system :refer [sys add-component start-system]]
            [bones.conf :as conf]
            [manifold.stream :as ms]))

(defn setup [test-fn]
  (add-component :conf (conf/map->Conf {:files ["conf/test.edn"]}))
  (add-component :redis (redis/map->Redis {}))
  (start-system :redis)
  (test-fn))

(use-fixtures :each setup)

(deftest pubsub
  (testing "pubsub"
    (let [stream (ms/stream)
          sub (redis/subscribe "123" stream)
          _ (redis/publish "123" "hello")
          result (ms/take! stream)]
      (redis/close-listener sub)
      (is (= "hello" @result)))))

(deftest component
  (testing "inline conf"
    (add-component :redis (redis/map->Redis {:channel-prefix "abc"
                                            :spec {:host "somewhere.else"
                                                   :port 123}}))
    (start-system :redis)
    (is (= "abc" (get-in @sys [:redis :channel-prefix])))
    (is (= 123 (get-in @sys [:redis :spec :port]))))

  (testing "conf overrides values"
    (swap! sys assoc-in [:conf :redis :spec] "xyz")
    (swap! sys assoc-in [:redis :spec] "abc")
    (start-system :redis)
    (is (= "xyz" (get-in @sys [:redis :spec])))))
