(ns weather-report.core
  (:gen-class)
  (require [com.stuartsierra.component :as component]
           [schema.core :as s]
           [bones.http.core :as http]))

(def sys (atom {}))

(defn login [args req]
  {:user-id 123 :roles ["tester"]})

(defn add-account [args req]
  args)

(def conf
  {:http/auth {:secret "a 16 byte stringa 32 byte string"}
   :http/handlers {:mount-path "/api"}})

(def commands
  [[:add-account {:account/xact-id s/Int
                  :account/evo-id (s/maybe s/Int)}
    :weather-report.core/add-account]
   [:login {:username s/Str :password s/Str}]])

(defn init-system [config]
  (http/build-system sys config)
  (http/register-commands commands))

(defn -main
  "The entry-point for 'lein run'"
  [& args]
  ;; join? true will block the caller forever
  (init-system (assoc-in conf [:http/service :join?] true))
  (http/start-system sys))

(comment
  ;; for the repl
  (println "hi")
  (init-system conf)
  (http/start-system sys)
  (http/stop-system sys)
  )
