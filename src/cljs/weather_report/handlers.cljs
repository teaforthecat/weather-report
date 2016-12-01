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


;; the first of the second param is what we dispatch on
;; the "trim-v" middleware must not be used here
;; [db [event-id stuff...]]
(defmulti handler (fn [db [channel & args]] channel))

(defmethod handler :initialize-db
  [_ [channel sys]]
  (merge db/default-db
         {:sys sys}))

(defmethod handler :set-active-panel
  [db [channel active-panel]]
  (assoc db :active-panel active-panel))

(defmethod handler :component/transition
  [db [channel component-name update-fn]]
  (update-in db [:components component-name] update-fn))

(defmethod handler :undo/do-undo
  [db [channel]]
  (let [[undo & undos] (:undos db)]
    (if undo
      (re-frame/dispatch undo))
    (assoc db :undos undos)))

(defmethod handler :component/store
  [db [channel component-name component-value]]
  (db/set-storage-item component-name component-value)
  (re-frame/dispatch [:component/transition component-name (constantly component-value)])
  db)

(defn undo-for [db command-event]
  (let [command (:command command-event)
        {:keys [:account/xact-id :account/evo-id]} (:args command-event)
        ;; important!!! this will delete the account - We only have add and delete so far
        args (if evo-id
               {:account/xact-id xact-id :account/evo-id nil}
               ;; if evo-id is nil we are deleting it, so find the current one for undo (to undelete it)
               (select-keys
                (first (filter #(= (int xact-id)
                                   ((fnil int 0) (:account/xact-id %)))
                               (:accounts db)))
                [:account/xact-id :account/evo-id]))]
    [:request/command command args {:undo true}]))

(defmethod handler :request/command
  [db [channel command args tap callback]]
  (let [c (:client @(:sys db))
        more-tap (assoc tap :command command :args args)]
    (client/command c command args more-tap)
    (if (:undo tap)
      db ;; no redo yet
      (update-in db [:undos] conj (undo-for db more-tap)))))

(defmethod handler :request/query
  [db [channel params tap]]
  (let [c (:client @(:sys db))]
    (client/query c params tap))
  db)

(defmethod handler :request/login
  [db [channel fields tap]]
  (let [c (:client @(:sys db))]
    (client/login c fields tap))
  db)

(defmethod handler :request/logout
  [db [channel tap]]
  (let [c (:client @(:sys db))]
    (client/logout c tap))
  db)

(defmethod handler :response/login
  [db [channel response status tap]]
  (if-let [sys (:sys db)]
    (let [success (condp = status
                    ;; can't establish connection
                    0 (do (client/stop sys)
                          false)
                    ;; start again to connect to event-stream
                    200 (let [share (response "share")]
                          (client/stop sys)
                          (client/start sys)
                          (re-frame/dispatch [:request/query {:accounts :all}])
                          ;; reset form
                          (re-frame/dispatch [:component/transition
                                              :login-form
                                              #(assoc % :errors {} :form {} :show false)])
                         ;; store user-info because it is only available in the login response
                          (re-frame/dispatch [:component/store :user-info share])
                          true)
                    401 (do
                          (swap! (:errors tap) assoc :message "Username or Password are incorrect")
                          false)
                    500 (do
                         (re-frame/dispatch [:component/transition
                                             :login-form
                                             #(assoc % :errors
                                                     {:message "There was a problem communicating with the server"})])
                          false)

                    (do
                      (js/console.log "error!")
                      false))]
      (assoc db :bones/logged-in? success))
    db))

(defmethod handler :response/logout
  [db [channel response status tap]]
  (if (= 200 status)
    (do
      (client/stop (:sys db))
      (re-frame/dispatch [:component/store :user-info nil])
      (assoc db :bones/logged-in? false))
    db))

(defmethod handler :response/command
  [db [channel response status tap]]
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

(defmethod handler :response/query
  [db [channel response status tap]]
  (if (= 200 status)
    (let [results (or (:results response) [])
          accounts (mapv #(-> %
                             (assoc :account/xact-id (:key %))
                             (assoc :account/evo-id (get-in % [:value "evo-id"])))
                        results)]
      (assoc db :accounts accounts))
    ;; todo error reporting
    db))

(defmethod handler :event/client-status
  [db [channel event]]
  (if-let [logged-in? (:bones/logged-in? event)]
    (assoc db :bones/logged-in? true)
    db))

(defmethod handler :event/message
  [db [channel event]]
  (let [{:keys [:account/xact-id :account/evo-id]} event]
    (if (nil? evo-id)
      (update db :accounts (partial remove
                                   ;; todo fix str->int
                                    #(= (int xact-id) (int (:account/xact-id %)))))
      (update db :accounts conj {:account/xact-id xact-id
                                 :account/evo-id evo-id}))))

(defn register-channel [channel]
  (re-frame/register-handler channel [re-frame.middleware/debug] handler))

;; non-lazy initializer
(doseq [h (keys (methods handler))]
  (register-channel h))

;; maybe??
;; to register a handler in another namespace use this
(defmacro register
  "register an event handler.
   be sure to return the db in the function body.
   be sure to match the signature for db and channel here:
   usage: (register :some/event [db [channel event args...]]
            (function body)
            db)
  "
  [channel args & body]
  `(do
     (register-channel ~channel)
     (defmethod handler ~channel ~args ~@body)))
