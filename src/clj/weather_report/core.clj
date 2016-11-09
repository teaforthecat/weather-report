(ns weather-report.core
  (:gen-class)
  (:require [com.stuartsierra.component :as component]
            [schema.core :as s]
            [manifold.stream :as ms]
            [bones.http :as http]
            [bones.stream.core :as stream]
            [bones.stream.kafka :as kafka]
            [weather-report.worker :as worker]
            [bones.stream.redis :as redis]))

(def sys (atom {}))

(defn login [args req]
  {:user-id 123 :roles ["tester"]})

(def login-schema {:username s/Str :password s/Str})

(defn add-account [args auth-info req]
  (let [{:keys [:account/xact-id :account/evo-id]} args
        producer (:producer @sys)]
    @(kafka/produce producer
                   "accounts"
                   ;; serialized to integer
                   (str xact-id)
                   ;; nil for compaction
                   (if evo-id {:evo-id evo-id} nil))))

(comment
  (add-account {:account/evo-id 123 :account/xact-id 456} {} {})
  (add-account {:account/evo-id 987 :account/xact-id 789} {} {})
  (add-account {:account/evo-id nil :account/xact-id 456} {} {})
  )

(defn format-event [request message]
  {;;:event "account-change"
   ;; must register handler for each event type
   :data message})

(defn event-stream [request auth-info]
  (let [user-id 1
        redis (:redis @sys)
        message-stream (ms/stream)
        _ (.subscribe redis "accounts" message-stream)
        events (ms/transform (map (partial format-event request))
                             message-stream)
        ]
    events))

(comment
  @redis/listeners
  (def e (ms/stream))
  (event-stream e {})
  (ms/consume println e)
  )

(def commands
  [[:add-account {:account/xact-id s/Int
                  :account/evo-id (s/maybe s/Int)}
    add-account]])

(defn query-handler [args auth-info req]
  (let [{:keys [account accounts]} args
        redi (:redis @sys)]
    (if account
      {:results @(redis/fetch redi account)}
      {:results @(redis/fetch-all redi "accounts")})))

(def query-schema {(s/optional-key :accounts) s/Any
                   (s/optional-key :account) s/Int})

(comment
  (query-handler {:accounts :all} {} {})
  (query-handler {:account 456} {} {})
  )

(def conf
  {:http/auth {:secret "a 16 byte stringa 32 byte string"
               :allow-origin "http://localhost:3449"}
   :http/service {:port 8080}
   :http/handlers {:mount-path "/api"
                   :login [login-schema login]
                   :commands commands
                   :query [query-schema query-handler]
                   :event-stream event-stream}
   :stream {:serialization-format :json-plain}})


(defn init-system [config]
  (http/build-system sys config)
  (stream/build-system sys config))

(defn -main
  "The entry-point for 'lein run'"
  [& args]
  ;; join? true will block the caller forever
  (init-system (assoc-in conf [:http/service :join?] true))
  (stream/start sys)
  (worker/connect sys)
  ;; this will block, must come last
  (http/start sys))

(comment
  ;; for the repl
  (println "hi")
  (init-system conf)
  (stream/start sys)
  (worker/connect sys)
  (http/start sys)
  (user/fig) ; frontend process
  (user/cljs) ; switch to browser repl `:cljs/quit' to switch back

  (http/stop sys)
  (stream/stop sys)

  )
