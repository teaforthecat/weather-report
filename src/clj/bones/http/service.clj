(ns bones.http.service
  (:require [io.pedestal.http :as http]
            [com.stuartsierra.component :as component]
            [io.pedestal.http :as server]))

(defprotocol Service
  (create [component]))

(defrecord Server [service conf]
  Service
  (create [cmp]
    (assoc cmp :server (server/create-server (:service cmp))))
  component/Lifecycle
  (start [cmp]
    (-> cmp
        (create)
        (update :server server/start)))
  (stop [cmp]
    (update cmp :server server/stop)))

(defn service [routes conf]
  {:env :prod
   ::server/join? false  ;; DEV ONLY
   :io.pedestal.http/routes  routes
   ::http/resource-path "/public"
   ::http/type :jetty
   ::http/port 8080
   ::http/container-options {:h2c? true
                             :h2? false
                             :ssl? false}})


(comment
  (io.pedestal.http.route.definition/defroutes routes
    [[["/api"
       ["/query" {:get [:bones/query  (fn [req] {:status 200 :body "hello"})]}]
       ["/command"
        {:post (bones.http.handlers/command-resource {})
         :get [:bones/commands-list (fn [req] {:status 200 :body (pr-str (bones.http.handlers/registered-commands))})]}]
       ]]])

  (def s (map->Server {:service  (service routes {})}))
  (def ss (create  s))
  (def sss (component/start ss)) ;; blocksssss.....
  (def ssss (component/stop sss))
  )
