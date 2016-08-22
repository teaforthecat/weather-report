(ns bones.stream.kafka
  (:require [clj-kafka.new.producer :as nkp]
            [clj-kafka.consumer.zk :as zkc]
            [bones.stream.serializer :as serializer]
            [com.stuartsierra.component :as component]
            [manifold.stream :as ms]
            [manifold.deferred :as d]))

(def default-format :msgpack )

(defprotocol Produce
  (produce [this topic key data])
  (produce-stream [this stream & {:as opts}]))

(defprotocol Consume
  (consume [this topic stream]))

(defrecord Producer [conf conn]
  component/Lifecycle
  (stop [cmp]
    (if (:producer cmp)
      (do
        (.close (:producer cmp))
        ;; dissoc changes the type
        (assoc cmp :conn nil :producer nil))
      cmp))
  (start [cmp]
    (let [config (get-in cmp [:conf :stream])
          producer-config (select-keys (merge {"bootstrap.servers" "127.0.0.1:9092"}
                                              config)
                                       ["bootstrap.servers"])
          {:keys [serialization-format]
           :or {serialization-format default-format}} config
          producer (nkp/producer producer-config
                                 ;; passthru key serializer
                                 (nkp/byte-array-serializer)
                                 ;; passthru value serializer
                                 (nkp/byte-array-serializer))
          ;; check for conn so we don't need a connection to run tests
          conn (if (:conn cmp)
                     (:conn cmp)
                     (partial nkp/send producer))]
      (-> cmp
          (assoc :config config) ;; for debugging
          (assoc :serialization-format serialization-format) ;; for debugging
          (assoc :serializer (serializer/encoder serialization-format))
          ;; store producer to call .close on
          (assoc :producer producer)
          ;; build a conn function that is easy to stub in tests
          (assoc :conn conn))))
  Produce
  (produce [cmp topic key data]
    (let [key-bytes (.getBytes key)
          data-bytes ((:serializer cmp) data)
          record (nkp/record topic
                             key-bytes
                             data-bytes)]
      ((:conn cmp) record)))
  (produce-stream [cmp stream & {:as opts}]
    (ms/consume
     #(let [{:keys [topic key value]} (merge opts %)]
        (produce cmp topic key value))
     stream)))

(defrecord Consumer [config conn]
  component/Lifecycle
  (stop [cmp]
    (zkc/shutdown (:consumer cmp))
    ;; dissoc changes the type
    (assoc cmp :conn nil :consumer nil))
  (start [cmp]
    (let [config (get-in cmp [:conf :stream])
          consumer-config (select-keys (merge {"zookeeper.connect" "127.0.0.1:2181"
                                               "group.id"  "bones.stream"
                                               "auto.offset.reset" "smallest"}
                                              config)
                                       ["zookeeper.connect"
                                        "group.id"
                                        "auto.offset.reset"])
          {:keys [serialization-format]
           :or {serialization-format default-format}} config
          ;; check for conn so we don't need a connection to run tests
          consumer (zkc/consumer consumer-config)
          conn (if (:conn cmp)
                 (:conn cmp)
                 (partial zkc/messages consumer))]
      (-> cmp
          (assoc :deserializer (serializer/decoder serialization-format))
          ;; store consumer to call .shutdown on
          (assoc :consumer consumer)
          ;; build a conn function that is easy to stub in tests
          (assoc :conn conn))))
  Consume
  (consume [cmp topic handler]
    (future
      (doseq [msg ((:conn cmp) topic)]
        (-> msg
             (update :key (:deserializer cmp))
             (update :value (:deserializer cmp))
             handler)))))
