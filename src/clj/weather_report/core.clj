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
            [weather-report.auth :as auth]
            [weather-report.worker :as worker]
            [clojure.string :as string]
            [bones.conf :as conf]))

(def sys (atom {}))

(defn login [args req]
  (let [conn (get-in @sys [:ldap])]
    (if-let [result (auth/authenticate conn
                                  (:username args)
                                  (:password args))]
      (with-meta result {:share [:groups :display-name]})
      nil ;; 401
      )))

(def login-schema {:username s/Str :password s/Str})

(defn add-account [args auth-info req]
  (let [{:keys [xact-id evo-id]} args
        producer (:producer @sys)]
    (merge {:args args}
           @(kafka/produce producer
                           "accounts"
                           ;; serialized to integer
                           (str xact-id)
                           ;; nil for compaction
                           (if evo-id {:evo-id evo-id} nil)))))

(comment
  (add-account {:evo-id 123 :xact-id 456} {} {})
  (add-account {:evo-id 987 :xact-id 789} {} {})
  (add-account {:evo-id nil :xact-id 456} {} {})
  )

(defn format-event [request message]
  (println (str "format-event: " message))
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
  [[:accounts/upsert {:xact-id s/Int
                      :evo-id s/Int}
    add-account]
   ;; update is required by bones.editable.forms save method
   [:accounts/update {:xact-id s/Int
                      :evo-id s/Int}
    add-account]
   [:accounts/delete {:xact-id s/Int
                      ;; nil or nothing
                      (s/optional-key :evo-id) (s/constrained (s/maybe s/Any) nil?)}
    add-account]])

(defn query-handler [args auth-info req]
  (let [{:keys [account accounts]} args
        redi (:redis @sys)]
    (if account
      {:results @(redis/fetch redi account)}
      {:results @(redis/fetch-all redi "accounts")})))

(def query-schema {(s/optional-key :accounts) s/Any
                   (s/optional-key :account) s/Int})

(defn conf []
  (bc/map->Conf
   ;; WR_ENV is a made up environment variable to set in a deployed environment.
   ;; The resolved file can be used to override the secret (and everything else in conf)
   {:conf-files ["config/common.edn" "config/ldap.edn" "config/$WR_ENV.edn"]
    :http/auth {:allow-origin "http://localhost:3449"}
    :http/service {:port 8080}
    :http/handlers {:mount-path "/api"
                    :login [login-schema login]
                    :commands commands
                    :query [query-schema query-handler]
                    :event-stream event-stream}
    :stream {:serialization-format :json-plain}}))

(defn build-system [system config]
  (let [cmp (if (= "some-condition" "is-true")
              (component/using (auth/map->LDAP {}) [:conf])
              (component/using (auth/map->FakeLDAP {}) [:conf]))]
    (swap! system assoc :ldap cmp)))

(defn init-system [config]
  (build-system sys config)
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

  (init-system (conf))
  (swap! sys component/start-system))

(comment
  ;; for the repl
  (println "hi")
  (-main) ; backend process
  (user/fig) ; frontend process
  (user/cljs) ; switch to browser repl `:cljs/quit' to switch back
  :cljs/quit

  (swap! sys component/stop-system)
  (swap! sys component/start-system)

  )
