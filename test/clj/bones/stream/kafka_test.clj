(ns bones.stream.kafka-test
  (:require [bones.conf :as conf]
            [bones.stream.kafka :as kafka]
            [clojure.test :refer [deftest is run-tests testing use-fixtures]]
            [com.stuartsierra.component :as component]
            [manifold.stream :as ms]
            [manifold.deferred :as d]))

(def producer-stub (constantly (future {:topic "topic" :partition 0 :offset 0})))
(def consumer-stub (constantly (lazy-seq [{:topic "topic" :offset 0 :partition 0
                                            :key (.getBytes "hello")
                                            :value (.getBytes "{\"abc\": 123}")}])))
(def sys (atom {}))
(swap! sys assoc :conf (conf/map->Conf {:files ["conf/test.edn"]
                                        :stream {:serialization-format :json}}))
(swap! sys assoc :producer (component/using
                            (kafka/map->Producer {:conn producer-stub})
                            [:conf]))
(swap! sys assoc :consumer (component/using
                            (kafka/map->Consumer {:conn consumer-stub})
                            [:conf]))
(swap! sys component/start-system [:conf])
(swap! sys component/start-system [:producer])
(swap! sys component/start-system [:consumer])

(def messages (atom []))

(defn topic-handler [message]
  (swap! messages conj message))

(deftest publish
  (testing "send to kafka"
    ;; this is pretty straight forward, but this is running the record
    (let [result @(.produce (:producer @sys) "topic" "key" {:data true})]
      (is (= @(producer-stub) result))))
  (testing "retreiving from kafka"
    (let [fut (.consume (:consumer @sys) "topic" topic-handler)]
      (let [_ @fut ;block
            msg (first @messages)]
        (is (= {"abc" 123} msg))))))
