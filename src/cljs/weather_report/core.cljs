(ns weather-report.core
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go go-loop]])
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [cljs.core.async :as a]
            [devtools.core :as devtools]
            [bones.client :as client]
            [weather-report.handlers :as handlers]
            [weather-report.subs]
            [weather-report.routes :as routes]
            [weather-report.views :as views]
            [weather-report.config :as config]))


(def sys (atom {}))

(defn dev-setup []
  (when config/debug?
    (println "dev mode")
    (devtools/install!)))

(defn mount-root []
  ;; it appears that this can't be done twice
  (if-let [app (.getElementById js/document "app")]
    (reagent/render [views/main-panel]
                    app)))

(defn ^:export init []
  (re-frame/dispatch-sync [:initialize-db sys])
  (dev-setup)
  (client/build-system sys {:url "http://localhost:8080/api"
                            :stream-handler re-frame/dispatch
                            :es/onopen js/console.log
                            :es/error js/console.log
                            :es/onmessage js/console.log
                            :es/connection-type :websocket})
  (client/start sys)
  (routes/app-routes) ;; todo: maybe not actually try to make a request
  ;; get the data connections setup,
  ;; then render
  (mount-root))

(comment
  (init)
  (js/alert "hi")

  (:stream-loop @sys)

  (get-in @sys [:client :client-state])
  (get-in @sys [:client :event-source])

  (remove
   #(= "456" (:account/xact-id %))
   @(re-frame.core/subscribe [:accounts])
   )

  @(re-frame.core/subscribe [:accounts])

  )
