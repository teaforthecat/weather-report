(ns weather-report.core
  (:gen-class)
  (:require [com.stuartsierra.component :as component]
            [clojure.spec.alpha :as s]
            [manifold.stream :as ms]
            [bones.conf :as bc]
            [bones.http :as http]
            [bones.stream.core :as stream]
            [bones.stream.kafka :as kafka]
            [bones.stream.redis :as redis]
            [weather-report.auth :as auth]
            [weather-report.worker :as worker]
            [weather-report.accounts :as accounts]
            [clojure.string :as string]
            [taoensso.timbre :as log]))

(def sys (atom {}))

(defn login [args req]
  (let [conn (get-in @sys [:ldap])]
    (if-let [result (auth/authenticate conn
                                  (:username args)
                                  (:password args))]
      ;; only share keys are added to the response body
      ;; this is because the result is encrypted into a token and not visible
      (with-meta result {:share [:groups :display-name]})
      nil ;; 401
      )))

;; (def login-schema {:username s/Str :password s/Str})
(s/def ::username string?)
(s/def ::password string?)
(s/def ::login (s/keys :req-un [::username ::password]))

(defn add-account [args auth-info req]
  (let [{:keys [xact-id evo-id]} args
        producer (:producer @sys)
        ;; I'm not sure where "default" comes from
        username (get-in auth-info ["default" :display-name])
        audit-src (if username (str "wr-admin-" username) "wr-admin")]
    (merge {:args args}
           @(kafka/produce producer
                           "xact-to-evo-data-share"
                           ;; must be a string
                           (str xact-id)
                           ;; nil for compaction
                           (if evo-id
                             {"evolution_account_id" evo-id
                              "src" audit-src}
                             nil)))))

(comment
  ;; create
  (add-account {:evo-id 123 :xact-id 456} {} {})
  ;; update
  (add-account {:evo-id 987 :xact-id 456} {} {})
  ;; delete
  (add-account {:evo-id nil :xact-id 456} {} {})
  )

(defn format-event [request message]
  {:data message})

(defn event-stream [request auth-info]
  (let [redis (:redis @sys)
        message-stream (ms/stream)
        _ (.subscribe redis "xact-to-evo-data-share" message-stream)
        events (ms/transform (map (partial format-event request))
                             message-stream)]
    events))

(comment
  @redis/listeners
  (def e (ms/stream))
  (event-stream e {})
  (ms/consume println e)
  )

(def commands
  [[:accounts/upsert ::accounts/upsert
    'weather-report.core/add-account]
   ;; update is required by bones.editable.forms save method
   [:accounts/update ::accounts/upsert
    'weather-report.core/add-account]
   [:accounts/delete ::accounts/delete
    'weather-report.core/add-account]])

(defn query-handler [args auth-info req]
  (let [{:keys [account accounts]} args
        redi (:redis @sys)]
    (if account
      {:results @(redis/fetch redi account)}
      {:results @(redis/fetch-all redi "xact-to-evo-data-share")})))

(def query-schema ::accounts/list)

(defn conf [args]
  (let [
        ;; the first arg may be a path to a conf file, which will have top priority
        conf-file-arg (first args)
        ;; the string "-use-fake-ldap" can be anywhere in the args
        use-fake-ldap (if args (< 0 (.indexOf args "-use-fake-ldap")))]
    (bc/map->Conf
     ;; WR_ENV is a made up environment variable to set in a deployed environment.
     ;; The resolved file can be used to override the secret (and everything else in conf)
     {:conf-files (remove nil? ["config/common.edn"
                                "config/ldap.edn"
                                ;; this is the file that puppet manages
                                "/opt/wr-admin/$APP_ENV.edn"
                                conf-file-arg])
      :use-fake-ldap use-fake-ldap
      ::http/service {:port 8080}
      ::http/handlers {:mount-path "/api"
                       :login [::login login]
                       :commands commands
                       :query [query-schema query-handler]
                       :event-stream event-stream}
      :stream {:serialization-format :json-plain}})))

(defn build-system [system config]
  (let [cmp (if (:use-fake-ldap config) ;; top level keyword
              (component/using (auth/map->FakeLDAP {}) [:conf])
              (component/using (auth/map->LDAP {}) [:conf]))]
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

  (init-system (conf args))
  (swap! sys component/start-system)
  (log/info (str "http server listening on port " (get-in @sys [:conf ::http/service :port])) )
  (log/info (str "http server allowing cors from " (get-in @sys [:conf ::http/auth :cors-allow-origin]))))

(comment
  ;; for the repl
  (println "hi")

  (-main nil "-use-fake-ldap") ; backend process
 ;; (-main nil)

  (user/fig) ; frontend process
  (user/cljs) ; switch to browser repl `:cljs/quit' to switch back
  :cljs/quit

  (swap! sys component/stop-system)

  (swap! sys component/start-system)

  )
