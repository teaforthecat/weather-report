(ns weather-report.core
  (:gen-class)
  (:require [com.stuartsierra.component :as component]
            [schema.core :as s]
            [manifold.stream :as ms]
            [bones.http.core :as http]
            [bones.stream.core :as stream]
            [bones.stream.kafka :as kafka]
            [weather-report.worker :as worker]
            [bones.stream.redis :as redis]))

(def sys (atom {}))

(defn login [args req]
  {:user-id 123 :roles ["tester"]})

(defn add-account [args req]
  (let [{:keys [:account/xact-id :account/evo-id]} args
        producer (:producer @sys)]
    @(kafka/produce producer
                   "accounts"
                   ;; serialized to integer
                   (str xact-id)
                   ;; nil for compaction
                   (if evo-id {:evo-id evo-id} nil))))

(comment
  (add-account {:account/evo-id 123 :account/xact-id 456} {})
  (add-account {:account/evo-id nil :account/xact-id 456} {})
  )

;; todo put this is conf file
(def conf
  {:http/auth {:secret "a 16 byte stringa 32 byte string"}
   :http/handlers {:mount-path "/api"}
   :stream {:serialization-format :json-plain}})

(def commands
  [[:add-account {:account/xact-id s/Int
                  :account/evo-id (s/maybe s/Int)}
    :weather-report.core/add-account]
   [:login {:username s/Str :password s/Str} ::login]])

(defn query-handler [args req]
  (let [{:keys [account accounts]} args
        redi (:redis @sys)]
    (if account
      {:results @(redis/fetch redi account)}
      {:results @(redis/fetch-all redi "accounts")})))

(comment
  (query-handler {:accounts :all} {})
  (query-handler {:account 456} {})
  )

(defn init-system [config]
  (http/build-system sys config)
  ;; this needs to move, not working
  ;; it works if evaluated after the server is started
  (http/register-commands commands)
  (http/register-query-handler ::query-handler {(s/optional-key :accounts) s/Any
                                                (s/optional-key :account) s/Int})
  (stream/build-system sys config))

(defn -main
  "The entry-point for 'lein run'"
  [& args]
  ;; join? true will block the caller forever
  (init-system (assoc-in conf [:http/service :join?] true))
  (stream/start-system sys)
  (worker/connect sys)
  ;; this will block, must come last
  (http/start-system sys))

(comment
  ;; for the repl
  (println "hi")
  (init-system conf)
  (stream/start-system sys)
  (worker/connect sys)
  (http/start-system sys)

  (http/stop-system sys)
  (stream/stop-system sys)
  )
