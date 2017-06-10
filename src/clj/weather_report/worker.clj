(ns weather-report.worker
  (:require [bones.stream.kafka :as kafka]
            [bones.stream.redis :as redis]
            [bones.stream.core :as stream]
            [com.stuartsierra.component :as component]))

(defn write-account-message [redis message]
  (let [k (:key message)
        v (:value message)
        offset (:offset message)
        ;; evo-id is a string here to keep the idea of json going from kafka to
        ;; redis. Nothing relies on this being json in redis though, so it could
        ;; change.
        different-evo-key (if v (assoc v "evo-id" (get v "evolution_account_id")))]
    ;; value needs to be possibly nil here for "compaction" in materialized view
    (redis/write redis "xact-to-evo-data-share" k different-evo-key)
    ;; converted from json
    (redis/publish redis
                   "xact-to-evo-data-share"
                   ;; (defmethod response/handler :event/message
                   ;; receives this on the client
                   {:evo-id (get v "evolution_account_id")
                    :src (get v "src")
                    :xact-id k})))

(defrecord Worker []
  component/Lifecycle
  (start [cmp]
    (let [consumer (:consumer cmp)
          conf (:conf cmp)
          redis (:redis cmp)]
      ;; use conf for group-id
      ;; todo how to start from beginning
      ;; returns future, never realized
      (kafka/consume consumer
                     "xact-to-evo-data-share"
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

