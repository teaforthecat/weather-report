(ns weather-report.core
  (:gen-class)
  (:require [com.stuartsierra.component :as component]
            [schema.core :as s]
            [manifold.stream :as ms]
            [clj-ldap.client :as ldap]
            [bones.conf :as bc]
            [bones.http :as http]
            [bones.stream.core :as stream]
            [bones.stream.kafka :as kafka]
            [bones.stream.redis :as redis]
            [weather-report.worker :as worker]
            [clojure.string :as string]
            [bones.conf :as conf]))

(def sys (atom {}))

(defn ldap-auth [ldap-pool username password attributes]
  (let [conn           (ldap/get-connection ldap-pool)
        qualified-name (str username "@" "stp01.office.gdi")]
    (try
      (if (ldap/bind? conn qualified-name password)
        (first (ldap/search conn
                            "OU=STP01,DC=stp01,DC=office,DC=gdi"
                            {:filter     (str "sAMAccountName=" username)
                             :attributes  attributes})))
      (finally (ldap/release-connection ldap-pool conn)))))


(defprotocol LDAPAuth
  (authenticate [this username password attributes]))

(defrecord LDAP [conf]
  component/Lifecycle
  (start [cmp]
         (if-let [ldap-conf (get-in cmp [:conf :ldap :con])]
           (assoc cmp :pool (ldap/connect ldap-conf))
           (assoc cmp :pool nil)))
  (stop [cmp]
        (if-let [pool (:pool cmp)]
          (do
            (ldap/close pool)
            (assoc cmp pool nil))
          cmp))
  LDAPAuth
  (authenticate [cmp username password attributes]
                (ldap-auth (:pool cmp) username password attributes)))

(comment

  (def ldap-pool
    (ldap/connect host))

 (ldap/who-am-i ldap-pool)
  (ldap/close ldap-pool)


  (def result  (authenticate "" ""))
  (:cn result)
  (:mail result)
  (:displayName result)


  (def groups
    (->> result
         (:msSFU30PosixMemberOf)
         (map #(string/split % #","))
         (map first)
         (map #(string/split % #"="))
         (map second)
         ))

  ) ;; comment

(defn parse-group [memberOf]
  (-> memberOf
   (string/split #",")
   first
   (string/split #"=")
   second))

(defn format-auth-info
  "extract info from Active Directory user info based on visual scan"
  [{:keys [mail displayName msSFU30PosixMemberOf]}]
  (let [groups (map parse-group msSFU30PosixMemberOf)]
    {:email mail
     :display-name displayName
     :groups groups}))

(defn login [args req]
  (let [conn (get-in @sys [:ldap])]
    (if-let [result (authenticate conn
                                  (:username args)
                                  (:password args)
                                  [:mail :displayName :msSFU30PosixMemberOf])]
      (format-auth-info result)
      nil ;; 401
      )))

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

(comment
  (conf/copy-values
   (merge-with conf/merge-maps {:http/service false}
               (conf/read-conf-data ["config/common.edn" "config/ldap.edn" "config/$WR_ENV.edn"]))
   [])

  (:ldap  (component/start (conf)))
  (component/stop (conf))

  )

(defn build-system [system config]
  (swap! system assoc :ldap (component/using (map->LDAP {}) [:conf])))

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
