(ns weather-report.core
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            ;; if this isn't used it will be compiled out in advanced compilation
            [devtools.core :as devtools]
            [re-frisk.core :refer [enable-re-frisk!]]
            [bones.client :as client]
            ;; load all the handlers immediately
            [weather-report.handlers]
            ;; load all the subs immediately
            [weather-report.subs]
            [weather-report.views :as views]
            [bones.editable :as e]
            [bones.editable.request :as request]
            [bones.editable.protocols :as p]))

;; overridden in min/production build
(goog-define api-uri "http://localhost:8080/api")

(when ^boolean js/goog.DEBUG
  (devtools/install!))

(defonce sys (atom {}))

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

(defn start-client [{:keys [start] :as args}]
  (client/stop sys)
  (client/start sys))

(defn ^:export init []
  (re-frame/dispatch-sync [:initialize-db])
  (when ^boolean js/goog.DEBUG
    (enable-re-frisk!))
  (client/build-system sys {:url weather-report.core/api-uri
                            :stream-handler re-frame/dispatch
                            :es/onopen client/log
                            :es/error client/log
                            ;; :es/onmessage js/console.log
                            :es/connection-type :websocket})

  ;; get the data connections setup,
  (client/start sys)
  (re-frame/reg-cofx :client #(assoc % :client (:client @sys)))
  (re-frame/reg-fx :start-client start-client) ;; for login handler

  ;; then render
  (mount-root))











(comment
  ;; for the repl
  (require '[re-frame.core :refer [dispatch]])
  (require '[cljs.pprint :refer [pprint]])
  (pprint @re-frame.db/app-db)



  (dispatch [:editable :login :new :state :show true])
  (dispatch [:editable :login :new :inputs :username "abcd"])
  (dispatch [:editable :login :new :inputs :password "abcd"])
  (dispatch [:request/login :login :new])

  (dispatch [:editable :accounts :new :state :show true])
  (dispatch [:editable :accounts :new :inputs :xact-id "123"])
  (dispatch [:editable :accounts :new :inputs :evo-id "456"])
  (dispatch [:request/command :accounts/upsert :new])

  (dispatch [:editable :accounts 123 :state :editing :evo-id true])
  (dispatch [:editable :accounts 123 :inputs :evo-id "789"])
  (dispatch [:request/command :accounts/upsert 123])









)
