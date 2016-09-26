(ns weather-report.worker
  (:require [bones.stream.kafka :as kafka]
            [bones.stream.redis :as redis]
            [bones.stream.core :as stream]))


(defn write-account-message [redis message]
  (let [k (:key message)
        v (:value message)
        offset (:offset message)]
    (redis/write redis "accounts" k v)
    ;; converted from json
    (redis/publish redis "accounts" {:account/evo-id (get v "evo-id")
                                     :account/xact-id k})))

(defn connect [sys]
  (let [consumer (:consumer @sys)
        redis (:redis @sys)]
    ;; todo how to start from beginning
    ;; todo set group-id
    ;; returns future, never realized
    ;; do deferreds consume memory if never derefed?
    (kafka/consume consumer
                   "accounts"
                   (partial write-account-message redis))))
