(ns weather-report.core
  (:gen-class)
  (:require [com.stuartsierra.component :as component]
            [schema.core :as s]
            [manifold.stream :as ms]
            [bones.conf :as bc]
            [bones.http :as http]
            [bones.stream.core :as stream]
            [bones.stream.kafka :as kafka]
            [bones.stream.redis :as redis]
            [weather-report.worker :as worker]))

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
  {;; event-types not supported
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

(def conf
  (bc/map->Conf
   ;; WR_ENV is a made up environment variable to set in a deployed environment.
   ;; The resolved file can be used to override the secret (and everything else in conf)
   {:files ["config/common.edn" "config/$WR_ENV.edn"]
    :http/auth {:allow-origin "http://localhost:3449"}
    :http/service {:port 8080}
    :http/handlers {:mount-path "/api"
                    :login [login-schema login]
                    :commands commands
                    :query [query-schema query-handler]
                    :event-stream event-stream}
    :stream {:serialization-format :json-plain}}))

(comment
  (component/start conf))

(defn init-system [config]
  (http/build-system sys config)
  (stream/build-system sys config)
  (worker/build-system sys config))

(defn -main
  "The entry-point for 'lein trampoline run'"
  [& args]
  (.addShutdownHook (Runtime/getRuntime)
                    (Thread. (fn []
                               (println "Shutting down...")
                               (swap! sys component/stop-system))))

  (init-system conf)
  (swap! sys component/start-system))

(comment
  ;; for the repl
  (println "hi")
  ;; (init-system conf)
  ;; (stream/start sys)
  ;; (worker/start sys)
  ;; (http/start sys)
  (-main)
  (user/fig) ; frontend process
  (user/cljs) ; switch to browser repl `:cljs/quit' to switch back

  (http/stop sys)
  (stream/stop sys)

  )
