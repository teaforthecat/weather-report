(ns weather-report.handlers
  (:import goog.net.Cookies)
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go go-loop]])
  (:require [re-frame.core :as re-frame]
            [re-frame.utils :refer [log error]]
            [weather-report.db :as db]
            [bones.client :as client]
            [cljs.core.async :as a]
            [schema.core :as s]))
(def debug?
  ^boolean js/goog.DEBUG)

(def mids  [(if debug? re-frame.middleware/debug)
            re-frame.middleware/trim-v])

(re-frame/register-handler
 :set-active-panel
 mids
 (fn [db [active-panel]]
   (assoc db :active-panel active-panel)))

(re-frame/register-handler
 :initialize-db
 mids
 (fn  [_ [sys]]
   (merge db/default-db
          {:sys sys})))

(defn req-query-handler [db [params tap]]
  (let [c (:client @(:sys db))]
    (client/query c params tap))
  db)

(defn req-command-handler [db [params tap]]
  (let [c (:client @(:sys db))]
    (client/command c params tap))
  db)

(defn req-login-handler [db [fields tap]]
  (let [c (:client @(:sys db))]
    (client/login c fields tap))
  db)

(defn req-logout-handler [db [form]]
  (let [c (:client @(:sys db))]
    (client/logout c {:form form}))
  db)

(def request-handlers [[:request/query req-query-handler
                        :request/command req-command-handler
                        :request/login req-login-handler
                        :request/logout req-logout-handler]])


(defn login-handler [db [response status tap]]
  (if-let [sys (:sys db)]
    (condp = status
      ;; can't establish connection
      0 (client/stop sys)
      ;; start again to connect to event-stream
      200 (do
            (client/start sys)
            (swap! (:form tap) assoc :enabled? false))
      (js/console.log "error!")))
  db)

(defn logout-handler [db [response status tap]]
  (if (= 200 (:status response))
    (if-let [sys (:sys db)]
      (do
        (client/stop sys)
        (swap! (:form tap) assoc :enabled? false)))
    db))

(defn command-handler [db [response status tap]]
  (let [{:keys [status]} response
        {{:keys [:account/xact-id :account/evo-id]} :body} response]
    (if (= 200 status)
      (let [accounts (:accounts db)]
        (if (nil? evo-id)
          (update db :accounts (partial remove
                                        #(= xact-id (:account/xact-id %))))
          (update db :accounts conj {:account/xact-id xact-id
                                     :account/evo-id evo-id})))
      ;; todo error handling
      db)))

(defn query-handler [db [response status tap]]
  (if (= 200 status)
    (let [results (or (:results response) [])
          accounts (mapv #(-> %
                             (assoc :account/xact-id (:key %))
                             (assoc :account/evo-id (get-in % [:value "evo-id"])))
                        results)]
      (assoc db :accounts accounts))
    ;; todo error reporting
    db))

(defn account-change-handler [db [event]]
  db)

(def response-handlers [[:response/login login-handler {s/Any s/Any}]
               [:response/logout logout-handler {s/Any s/Any}]
               [:response/query query-handler {:results [s/Any]}]
               [:response/command command-handler {s/Any s/Any}]
               [:event/account-change account-change-handler {:account/xact-id s/Int
                                                              :account/evo-id (s/maybe s/Int)
                                                              s/Any s/Any}]])

(def schema-check
  "check the schema before any app code sees the revent"
  ^{:re-frame-factory-name "path"}
  (fn schema-check-handler
    [schema]
    (fn schema-check-middleware
      [handler]
      (fn schema-check-handler
        [db v]
        ;; second item in event vector is response or event - if trimv hasn't
        ;; been applied yet - watch out
        (if-let [errors (s/check schema (second v))]
          (do
            (error "schema error handling event vector: " v)
            db)
          (handler db v))))))

(defn register [[channel handler schema]]
  (re-frame/register-handler channel
                             mids
                             handler))
