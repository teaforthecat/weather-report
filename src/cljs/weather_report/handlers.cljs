(ns weather-report.handlers
  (:require [bones.client :as c]
            [bones.editable.helpers :as h]
            [bones.editable.response :as response]
            [re-frame.core :as re-frame]
            [weather-report.db :as db]))

(def debug?
  ^boolean js/goog.DEBUG)

(defn log [msg]
  (when debug?
    (.log js/console msg)))

(re-frame.core/reg-fx :log log)

(re-frame.core/reg-event-db
 :initialize-db
 (fn [_ _]
   db/default-db))

(defmethod response/handler [:response/login 200]
  [{:keys [db client]} [channel response status tap]]
  (let [{:keys [e-scope]} tap
        [_ e-type identifier] e-scope]
    {:dispatch (h/editable-reset e-type identifier {})
     :start-client {:start true}}))

(defmethod response/handler [:response/login 401]
  [{:keys [db client]} [channel response status tap]]
  (let [{:keys [e-scope]} tap
        [_ e-type identifier] e-scope]
    {:dispatch (into e-scope [:errors :message "Invalid username or password"])}))

(defmethod response/handler [:response/login 502]
  [{:keys [db client]} [channel response status tap]]
  (let [{:keys [e-scope]} tap
        [_ e-type identifier] e-scope]
    {:dispatch (into e-scope [:errors :message "The Server responded with 'Bad Gateway'."])}))

(defmethod response/handler [:response/login 0]
  [{:keys [db client]} [channel response status tap]]
  (let [{:keys [e-scope]} tap
        [_ e-type identifier] e-scope]
    {:dispatch (into e-scope [:errors :message "Could not connect to server."])}))

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

(defmethod response/handler [:response/command 200]
  [{:keys [db]} [channel response status tap]]
  (let [{:keys [e-scope args command]} tap
        [_ e-type identifier] e-scope]
    (cond
      (= identifier :new)
      {:dispatch (h/editable-response e-type identifier response)}
      (= command :accounts/update)
      ;; the state gets updated in the :event/message handler
      {:log {:message "response received, waiting for events..."}}
      (= command :accounts/delete)
      {:log {:message "response received, waiting for events..."}})))

(defmethod response/handler :event/client-status
  [{:keys [db]} [channel event]]
  (let [logged-in? (:bones/logged-in? event)]
    (if logged-in?
      {:dispatch [:request/query {:accounts :all}]
       :db (assoc db :bones/logged-in? true)}
      {:db (assoc db :bones/logged-in? false)})))

(defmethod response/handler :event/message
  [{:keys [db]} [channel message]]
  (let [id (:xact-id message)
        evo-id (:evo-id message)]
    (if evo-id
      ;; upsert
      {:dispatch [:editable [:editable :accounts id :inputs message]
                  [:editable :accounts id :state :editing :evo-id false]]}
      ;; deletion
      {:db (update-in db [:editable :accounts] dissoc id)})))
