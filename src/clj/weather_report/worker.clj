(ns weather-report.worker
  (:require [bones.stream.kafka :as kafka]
            [bones.stream.redis :as redis]))

(defn connect [sys]
  (let [consumer (:consumer @sys)
        redis (:redis @sys)]
    ;; todo how to start from beginning
    ;; todo set group-id
    ;; returns future, never realized
    (kafka/consume consumer
                   "accounts"
                   ;; do deferreds consume memory if never derefed?
                   #(redis/write redis "accounts" (:key %) (:value %)))))
