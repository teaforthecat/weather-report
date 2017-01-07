(ns weather-report.handlers
  (:import goog.net.Cookies)
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go go-loop]])
  (:require [re-frame.core :as re-frame]
            ;; [re-frame.utils :refer [log error]]
            [weather-report.db :as db]
            [bones.editable :as e]
            [bones.editable.response :as response]
            [bones.editable.helpers :as h]
            [bones.client :as client]
            [cljs.core.async :as a]
            [schema.core :as s]))

(def debug?
  ^boolean js/goog.DEBUG)

(def mids  [(if debug? re-frame/debug)
            re-frame/trim-v])


;; the first of the second param is what we dispatch on
;; the "trim-v" middleware must not be used here
;; [db [event-id stuff...]]
(defmulti handler (fn [db [channel & args]] channel))

(defmethod handler :initialize-db
  [_ [channel sys]]
  db/default-db)

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

(defmethod handler [:response/login 200]
  [{:keys [db client]} [channel response status tap]]
  (let [{:keys [form-type identifier]} tap]
    (client/start client) ;; this should trigger :event/client-status
    {:dispatch (h/editable-reset form-type identifier {})}))

#_(defmethod handler :response/login
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

#_(defmethod handler :response/logout
  [db [channel response status tap]]
  (if (= 200 status)
    (do
      (client/stop (:sys db))
      (re-frame/dispatch [:component/store :user-info nil])
      (assoc db :bones/logged-in? false))
    db))

#_(defmethod handler :response/command
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

(defn result-to-editable-account [item]
  ;; TODO: conform to spec here instead
  (let [xact-id (js/parseInt (:key item))
        _evo-id (get-in item [:value "evo-id"])
        evo-id (if _evo-id (js/parseInt _evo-id))]
    {xact-id {:inputs {:xact-id xact-id :evo-id evo-id}}}))

(defmethod response/handler [:response/query 200]
  [{:keys [db]} [channel response status tap]]
  (let [results (or (:results response) [])
        accounts (into {} (map result-to-editable-account) results)]
    ;; need to keep the :_meta
    {:db (update-in db [:editable :accounts] merge accounts)}))

(defmethod response/handler :event/client-status
  [{:keys [db]} [channel event]]
  (let [logged-in? (:bones/logged-in? event)]
    (if logged-in?
      {:dispatch [:request/query {:accounts :all}]
       :db (assoc db :bones/logged-in? true)}
      {:db (assoc db :bones/logged-in? false)})))

#_(defmethod handler :event/message
  [db [channel event]]
  (let [{:keys [:account/xact-id :account/evo-id]} event]
    (if (nil? evo-id)
      (update db :accounts (partial remove
                                   ;; todo fix str->int
                                    #(= (int xact-id) (int (:account/xact-id %)))))
      (update db :accounts conj {:account/xact-id (int xact-id)
                                 :account/evo-id (int evo-id)}))))

(defn register-channel [channel]
  (re-frame/reg-event-db channel [re-frame/debug] handler))

;; non-lazy initializer
(doseq [h (keys (methods handler))]
  (register-channel h))
