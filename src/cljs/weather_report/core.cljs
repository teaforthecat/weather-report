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

(defn subscribe-client [system]
  (re-frame/register-sub
   :bones/client-state
   (fn [db _]
     (reaction @(get-in @system [:client :client-state])))))

(defn stream-loop [c]
  (go-loop []
    (let [revent (a/<! (client/stream c))
          body (or (get-in revent [:response :body])
                   (:event revent)
                   (throw "uh oh no response body or event"))]
      (println "receiving" revent)
      (re-frame/dispatch [(:channel revent)
                          body
                          (get-in revent [:response :status])
                          (:tap revent)]))
    (recur)))

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
  ;; register must not be lazy
  (doseq [h handlers/request-handlers]
    (handlers/register h))
  (doseq [h handlers/response-handlers]
    (handlers/register h))
  (re-frame/dispatch-sync [:initialize-db sys])
  (dev-setup)
  (client/build-system sys {:url "http://localhost:8080/api"
                            :es/onopen js/console.log
                            :es/error js/console.log
                            })
  (client/start sys)
  (subscribe-client sys)
  (routes/app-routes) ;; this actually tries to make a request
  (swap! sys assoc :stream-loop (stream-loop (:client @sys)))
  ;; get the data connections setup,
  ;; then render
  (mount-root))

(comment
  (:stream-loop @sys)

  ;; (get-in sys [:client])
  )
