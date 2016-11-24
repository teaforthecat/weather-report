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

(re-frame/register-handler
 :component/show
 mids
 (fn [db [component-name]]
   (assoc-in db [:components component-name :show] true)))

(re-frame/register-handler
 :component/hide
 mids
 (fn [db [component-name callback]]
   (if callback (callback))
   (assoc-in db [:components component-name :show] false)))

(re-frame/register-handler
 :component/transition
 mids
 (fn [db [component-name update-fn]]
   (update-in db [:components component-name] update-fn)))

(comment
  (re-frame.core/dispatch [:component/transition :login-form #(assoc % :show true)])
  (re-frame.core/dispatch [:component/transition :login-form #(assoc % :show false)])

  (re-frame.core/dispatch [:request/logout])
  (println
   (get-in @re-frame.db/app-db [:components :login-form ])

   )

  )

(defn request-query-handler [db [params tap]]
  (let [c (:client @(:sys db))]
    (client/query c params tap))
  db)

(defn request-command-handler [db [command args tap callback]]
  (let [c (:client @(:sys db))
        more-tap (assoc tap :command command :args args)]
    (client/command c command args more-tap))
  db)

(defn request-login-handler [db [fields tap]]
  (let [c (:client @(:sys db))]
    (client/login c fields tap))
  db)

(defn request-logout-handler [db [tap]]
  (let [c (:client @(:sys db))]
    (client/logout c tap))
  db)

(def request-handlers [[:request/query request-query-handler]
                       [:request/command request-command-handler]
                       [:request/login request-login-handler]
                       [:request/logout request-logout-handler]])

(defn response-login-handler [db [response status tap]]
  (if-let [sys (:sys db)]
    (let [success (condp = status
                    ;; can't establish connection
                    0 (do (client/stop sys)
                          false)
                    ;; start again to connect to event-stream
                    200 (do
                          (client/stop sys)
                          (client/start sys)
                          (re-frame/dispatch [:request/query {:accounts :all}])
                          (re-frame/dispatch [:component/hide :login-form])
                          true)
                    401 (do
                          (swap! (:errors tap) assoc :message "Username or Password are incorrect")
                          false)
                    (do
                      (js/console.log "error!")
                      false))]
      (assoc db :bones/logged-in? success))
    db))

(defn response-logout-handler [db [response status tap]]
  (if (= 200 status)
    (do
      (client/stop (:sys db))
      (assoc db :bones/logged-in? false))
    db))

(defn response-command-handler [db [response status tap]]
  (cond
    (= 200 status)
    (do
      (let [cmp (:command tap)]
        ;; the command happens to match the component
        (re-frame/dispatch [:component/transition
                            cmp
                            #(assoc %
                                    :show false
                                    :form {}
                                    :errors {})]))
      )
    (= 400 status)
    (let [invalid-args (:args response)]
      (cond
        (= 'missing-required-key (get-in invalid-args [:account/xact-id]))
        (swap! (:errors tap) assoc :message "Xact-id is required")

        (= 'missing-required-key (get-in invalid-args [:account/evo-id]))
        (swap! (:errors tap) assoc :message "Evo-id is required")

        (= '(not (integer? nil)) (get-in invalid-args [:account/xact-id]))
        (swap! (:errors tap) assoc :message "xact-id must be an integer")

        (= '(not (integer? nil)) (get-in invalid-args [:account/evo-id]))
        (swap! (:errors tap) assoc :message "Evo-id must be an integer")
        )))
  db)

(defn response-query-handler [db [response status tap]]
  (if (= 200 status)
    (let [results (or (:results response) [])
          accounts (mapv #(-> %
                             (assoc :account/xact-id (:key %))
                             (assoc :account/evo-id (get-in % [:value "evo-id"])))
                        results)]
      (assoc db :accounts accounts))
    ;; todo error reporting
    db))

(defn event-client-state-change [db [event]]
  (if-let [logged-in? (:bones/logged-in? event)]
    (assoc db :bones/logged-in? true)
    db))

(defn event-account-change [db [event]]
  (let [{:keys [:account/xact-id :account/evo-id]} event]
    (if (nil? evo-id)
      (update db :accounts (partial remove
                                   ;; todo fix str->int
                                    #(= (int xact-id) (int (:account/xact-id %)))))
      (update db :accounts conj {:account/xact-id xact-id
                                 :account/evo-id evo-id}))))

(def response-handlers [[:response/login response-login-handler {s/Any s/Any}]
                        [:response/logout response-logout-handler {s/Any s/Any}]
                        [:response/query response-query-handler {:results [s/Any]}]
                        [:response/command response-command-handler {s/Any s/Any}]
                        [:event/client-status event-client-state-change {(s/optional-key :bones/logged-in?) s/Bool}]
                        ;; there is only client-status and message so far
                        [:event/message event-account-change {:account/xact-id s/Int
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
        ;; been applied yet - watch out for the order
        (if-let [errors (s/check schema (second v))]
          (do
            (error "schema error handling event vector: " v)
            db)
          (handler db v))))))

(defn register [[channel handler schema]]
  (re-frame/register-handler channel
                             mids
                             handler))

(doseq [h request-handlers]
  (register h))
(doseq [h response-handlers]
  (register h))
