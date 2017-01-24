(ns weather-report.core
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            ;; if this isn't used it will be compiled out in advanced compilation
            [devtools.core :as devtools]
            [bones.client :as client]
            ;; load all the handlers immediately
            [weather-report.handlers]
            ;; load all the subs immediately
            [weather-report.subs]
            [weather-report.views :as views]
            [bones.editable :as e]
            [bones.editable.request :as request]
            [bones.editable.protocols :as p]
            ))

(when ^boolean js/goog.DEBUG
  (devtools/install!))

(def sys (atom {}))

(defn mount-root []
  (reagent/render-component [views/main-panel]
                            (.getElementById js/document "app")))

;; glue the two libraries together
(extend-type client/Client
  p/Client
  (login [cmp args tap]
    (client/login cmp args tap))
  (logout [cmp tap]
    (client/logout cmp tap))
  (command [cmp cmd args tap]
    (client/command cmp cmd args tap))
  (query [cmp args tap]
    (client/query cmp args tap)))

(defn ^:export init []
  (re-frame/dispatch-sync [:initialize-db])
  (client/build-system sys {:url "http://localhost:8080/api"
                            :stream-handler re-frame/dispatch
                            :es/onopen js/console.log
                            :es/error js/console.log
                            :es/onmessage js/console.log
                            :es/connection-type :websocket})
  (client/start sys)
  (request/set-client (:client @sys))
  ;; get the data connections setup,
  ;; then render
  (mount-root))
