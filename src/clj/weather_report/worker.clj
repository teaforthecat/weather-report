(ns weather-report.worker
  (:require [bones.stream.kafka :as kafka]
            [bones.stream.redis :as redis]
            [bones.stream.core :as stream]
            [com.stuartsierra.component :as component]))


(defn write-account-message [redis message]
  (let [k (:key message)
        v (:value message)
        offset (:offset message)]
    (println (str "publishing key: " (:key message)))
    (println (str "publishing value: " (:value message)))
    (redis/write redis "accounts" k v)
    ;; converted from json
    (redis/publish redis "accounts" {:evo-id (get v "evo-id")
                                     :xact-id k})))

(defrecord Worker [conf consumer redis]
  component/Lifecycle
  (start [cmp]
    (let [consumer (:consumer cmp)
          redis (:redis cmp)]
      ;; use conf for group-id
      ;; todo how to start from beginning
      ;; returns future, never realized
      (kafka/consume consumer
                     "accounts"
                     (partial write-account-message redis))
      (assoc cmp :accounts-worker :working)))
  (stop [cmp]
    ;; nothing to do here
    (assoc cmp :accounts-worker nil)))

(defn build-system
  "incorporate Worker into system and declare dependencies"
  [sys config]
  ;; consumer and redis are from bones.stream
  (swap! sys #(-> %
                  (assoc :conf config)
                  (assoc :worker (component/using (map->Worker {}) [:conf :consumer :redis])))))

