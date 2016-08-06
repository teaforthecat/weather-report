(ns weather-report.handlers
  (:require [re-frame.core :as re-frame]
            [weather-report.db :as db]
            [bones.client :as client]
            [cljs.core.async :as a]))

(re-frame/register-handler
 :initialize-db
 (fn  [_ _]
   db/default-db))

(re-frame/register-handler
 :set-active-panel
 (fn [db [_ active-panel]]
   (assoc db :active-panel active-panel)))

(defmulti submit-form (fn [form-id _ _ _] form-id))

(defmethod submit-form :login [form-id db form default-form]
  (let [form-data (select-keys @form [:username :password])]
    (a/take! (client/post "http://localhost:8080/api/login"
                          {:command :login
                           :args form-data})
             #(re-frame/dispatch [:login-handler % form default-form]))
    db))

(defmethod submit-form :add-account [form-id db form default-form]
  (let [form-data (select-keys @form [:account/xact-id :account/evo-id])]
    (a/take! (client/post "http://localhost:8080/api/command"
                          {:command :add-account
                           :args form-data})
             #(re-frame/dispatch [:add-account-handler % form default-form]))
    db))

(re-frame/register-handler
 :submit-form
 (fn [db [_ form-id form default-form]]
  (submit-form form-id db form default-form)))

(re-frame/register-handler
 :logout
 (fn [db [_ form-ratom]]
   (assoc db :bones/logged-in? false)))

(re-frame/register-handler
 :login-handler
 (fn [db [_ response form default-form]]
   (if (= 200 (:status response))
     (do
       (reset! form default-form)
       (assoc db :bones/logged-in? true))
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
