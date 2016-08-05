(ns weather-report.core
  (:gen-class)
  (require [com.stuartsierra.component :as component]
           [schema.core :as s]
           [bones.http.service :as service]
           [bones.http.handlers :as handlers]
           [bones.conf :as conf]))

(def sys (atom {}))
(defn login [args req]
  ;; {:user-id 123 :roles ["tester"]})
  true)

(handlers/register-command :login {:username s/Str :password s/Str} )

(defn init-system []
  ;; note: this could also be a Conf component
  (swap! sys assoc :conf {:http/handlers {:mount-path "/api"}})
  (swap! sys assoc :routes (component/using
                            (handlers/map->CQRS {})
                            [:conf]))
  (swap! sys assoc :http (component/using
                          (service/map->Server {})
                          [:conf :routes])))

(defn -main
  "The entry-point for 'lein run'"
  [& args]
  (init-system)
  (swap! sys assoc-in [:conf :http/service :join?] true) ;; this will block the caller forever
  (service/start-system sys :http :routes :conf))

(comment
  (service/stop-system sys :http)
  )
