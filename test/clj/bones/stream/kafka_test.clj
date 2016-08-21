(ns bones.stream.kafka-test
  (:require [bones.conf :as conf]
            [bones.stream.kafka :as kafka]
            [clojure.test :refer [deftest is run-tests testing use-fixtures]]
            [com.stuartsierra.component :as component]
            [manifold.stream :as ms]
            [manifold.deferred :as d]))

(defn producer-stub [record]
  ;; really returns something like this:
  ;; {:topic "topic" :partition 0 :offset 0}
  ;; but here we'll return the arg to test the serialization
  (future record))

;; a mock KafkaMessage
(def consumer-stub (constantly (lazy-seq [{:topic "topic" :offset 0 :partition 0
                                            :key (.getBytes "456")
                                            :value (.getBytes "{\"abc\": 123}")}])))
;; helper function
(defn to-s [bytearray]
  (apply str (map char bytearray)))

(def sys (atom {}))
(swap! sys assoc :conf (conf/map->Conf {:files ["conf/test.edn"]
                                        :stream {:serialization-format :json-plain}}))
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

(deftest serialization
  (testing "send to kafka - the serializer"
    (let [result @(.produce (:producer @sys) "topic" "key" {:data true})]
      (is (= "{\"data\":true}" (apply str (map char (.value result)))))))
  (testing "retreiving from kafka - the deserializer"
    (let [fut (.consume (:consumer @sys) "topic" topic-handler)]
      (let [ ;blocking deref for tests only, this should not be called in the application
            f @fut
            msg (first @messages)]
        (is (= nil f)) ;; (doseq returns nil)
        (is (= 0           (:offset msg)))
        (is (= 0           (:partition msg)))
        (is (= "topic"     (:topic msg)))
        (is (= 456         (:key msg)))
        (is (= {"abc" 123} (:value msg)))))))
