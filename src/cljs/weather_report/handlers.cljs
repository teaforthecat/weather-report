(ns weather-report.handlers
  (:import goog.net.Cookies)
  (:require [re-frame.core :as re-frame]
            [weather-report.db :as db]
            [bones.client :as client]
            [cljs.core.async :as a]))

(defn get-cookie [cookie-name]
  (.get (Cookies. js/document) cookie-name))

(defn set-cookie [cookie-name value]
  (.set (Cookies. js/document) cookie-name value))

(defn delete-cookie [cookie-name]
  (.remove (Cookies. js/document) cookie-name))

(re-frame/register-handler
 :initialize-db
 (fn  [_ _]
   (if-let [token (get-cookie "bones-token")]
     (assoc db/default-db :bones/token token)
     db/default-db)))

(re-frame/register-handler
 :set-active-panel
 (fn [db [_ active-panel]]
   (assoc db :active-panel active-panel)))

(defmulti submit-form (fn [form-id _ _ _] form-id))

(defmethod submit-form :login [form-id db form default-form]
  (let [form-data (select-keys @form [:username :password])]
    (a/take! (client/login {:command :login
                           :args form-data})
             #(re-frame/dispatch [:login-handler % form default-form]))
    db))

(defmethod submit-form :add-account [form-id db form default-form]
  (let [form-data (select-keys @form [:account/xact-id :account/evo-id])
        token (get-in db [:bones/token])]
    (a/take! (client/command {:command :add-account
                              :args form-data}
                             token)
             #(re-frame/dispatch [:add-account-handler % form default-form]))
    db))

(re-frame/register-handler
 :submit-form
 (fn [db [_ form-id form default-form]]
  (submit-form form-id db form default-form)))

(re-frame/register-handler
 :logout
 (fn [db [_ form-ratom]]
   (delete-cookie "bones-token")
   (assoc db :bones/token false)))

(re-frame/register-handler
 :login-handler
 (fn [db [_ response form default-form]]
   (if (= 200 (:status response))
     (let [token (get-in response [:body :token])]
       (reset! form default-form)
       (re-frame/dispatch [:get-accounts token])
       (set-cookie "bones-token" token)
       (-> db
           (assoc :bones/token token)))
     (do
       (println response)
       db))))

(re-frame/register-handler
 :add-account-handler
 (fn [db [_ response form default-form]]
   (if (= 200 (:status response))
     (let [data @form]
       (reset! form default-form)
       (update db :accounts conj data))
     (do
       (swap! form assoc :errors (:body response))
       (println response)
       db))))

(re-frame/register-handler
 :get-accounts-handler
 (fn [db [_ response]]
   (if (= 200 (:status response))
     (let [results (get-in response [:body :results])
           accounts (map #(-> %
                              (assoc :account/xact-id (:key %))
                              (assoc :account/evo-id (get-in % [:value "evo-id"])))
                         results)]
       (assoc db :accounts accounts))
     ;; todo error reporting
     db)))

(re-frame/register-handler
 :get-accounts
 (fn [db [_ auth-token]]
   (if-let [token (or auth-token (get-in db [:bones/token]))]
     (a/take! (client/query {:accounts :all} token)
              #(re-frame/dispatch [:get-accounts-handler %])))
   db))

(re-frame/register-handler
 :remove-account-handler
 (fn [db [_ response id]]
   (if (= 200 (:status response))
     (let [accounts (:accounts db)]
       (update db :accounts (partial remove
                                     #(= id (:account/xact-id %)))))
     ;; todo error handling
     db)))

(re-frame/register-handler
 :remove-account
 (fn [db [_ id]]
   (if-let [token (get-in db [:bones/token])]
     (a/take! (client/command {:command :add-account
                               :args {:account/xact-id (int id) :account/evo-id nil}} token)
              #(re-frame/dispatch [:remove-account-handler % id])))
   db))


(comment
  (re-frame/dispatch [:get-accounts])
  )
