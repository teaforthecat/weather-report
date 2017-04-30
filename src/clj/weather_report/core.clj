(ns weather-report.core
  (:gen-class)
  (:require [com.stuartsierra.component :as component]
            [clojure.spec :as s]
            [manifold.stream :as ms]
            [bones.conf :as bc]
            [bones.http :as http]
            [bones.stream
             [core :as stream]
             [jobs :as jobs]
             [protocols :as p]]
            [weather-report.auth :as auth]
            [weather-report.accounts :as accounts]
            [clojure.string :as string]))

(def sys (atom {}))

(defn login [args req]
  (let [conn (get-in @sys [:ldap])]
    (if-let [result (auth/authenticate conn
                                  (:username args)
                                  (:password args))]
      (with-meta result {:share [:groups :display-name]})
      nil ;; 401
      )))

;; (def login-schema {:username s/Str :password s/Str})
(s/def ::username string?)
(s/def ::password string?)
(s/def ::login (s/keys :req-un [::username ::password]))

(defn add-account [args auth-info req]
  (let [{:keys [xact-id evo-id]} args
        message {
                 ;; key needs to be a string(?)
                 :key (str xact-id)
                 ;; message can be nil for compaction
                 :message (if evo-id {:evo-id evo-id})}]

    (merge {:args args}
           (p/input (:job @sys) message))))

(comment
  ;; create
  (add-account {:evo-id 123 :xact-id 456} {} {})
  ;; update
  (add-account {:evo-id 987 :xact-id 456} {} {})
  ;; delete
  (add-account {:evo-id nil :xact-id 456} {} {})
  )

(defn format-event [request message]
  ;;; hmmm evo-id and xact-id are strings...
  (let [msg {:evo-id (get-in message [:message "evo-id"])
             :xact-id (get-in message [:key])}
        int-msg (s/conform ::accounts/upsert msg)]
    {:data int-msg}))

(defn event-stream [request auth-info]
  (let [message-stream (ms/stream)
        _ (p/output (:job @sys) message-stream)
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
      {:results @(p/fetch redi account)}
      {:results @(p/fetch-all redi "accounts")})))

(def query-schema ::accounts/list)

(defn conf [args]
  (let [conf-file-arg (first args)
        use-fake-ldap (< 0 (.indexOf args "-use-fake-ldap"))]
    (bc/map->Conf
     ;; WR_ENV is a made up environment variable to set in a deployed environment.
     ;; The resolved file can be used to override the secret (and everything else in conf)
     {:conf-files (remove nil? ["config/common.edn"
                                "config/dev-config.edn"
                                "config/ldap.edn"
                                "config/$WR_ENV.edn"
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
  (stream/build-system sys
                       ;; :accounts doesn't do anything yet
                       (jobs/bare-job :accounts "accounts")
                       config))

(defn -main
  "The entry-point for 'lein trampoline run'"
  [& args]
  (.addShutdownHook (Runtime/getRuntime)
                    (Thread. (fn []
                               (println "Shutting down...")
                               (swap! sys component/stop-system))))

  (init-system (conf args))
  (swap! sys component/start-system))

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
