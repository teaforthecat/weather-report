(ns bones.http.service
  (:require [com.stuartsierra.component :as component]
            [io.pedestal.http :as server]))

(defrecord Server [service conf]
  component/Lifecycle
  (start [cmp]
    (-> cmp
        (assoc :server (server/start (:service cmp))))))

(defn service [routes conf]
  {:env :prod
   ::http/routes routes
   ::http/resource-path "/public"
   ::http/type :jetty
   ::http/port 8080
   ::http/container-options {:h2c? true
                             :h2? false
                             :ssl? false}})
