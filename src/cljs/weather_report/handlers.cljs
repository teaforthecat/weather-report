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
  (let [form-data (select-keys @form [:username :password])
        response  (a/take! (client/post "http://localhost:8080/api/login"
                                        {:command :login
                                         :args form-data})
                           println) ]
   ;; move to handler
    (if (= 200 (:status response))
      (do
        (reset! form default-form)
        (assoc db :bones/logged-in? true))
      (println response))))

(defmethod submit-form :add-account [form-id db form default-form]
  (let [data @form]
    (reset! form default-form)
    (update db :accounts conj data)))

(re-frame/register-handler
 :submit-form
 (fn [db [_ form-id form default-form]]
  (submit-form form-id db form default-form)))

(re-frame/register-handler
 :logout
 (fn [db [_ form-ratom]]
   (assoc db :bones/logged-in? false)))
