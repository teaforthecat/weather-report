(ns weather-report.subs
  (:require-macros [reagent.ratom :refer [reaction]])
  (:require [re-frame.core :as re-frame]
            [weather-report.local-storage :as storage]))


(re-frame/reg-sub
 :name
 (fn [db]
   (:name @db)))

;; (re-frame/register-sub
;;  :active-panel
;;  (fn [db _]
;;    (reaction (:active-panel @db))))

;; (re-frame/register-sub
;;  :bones/logged-in?
;;  (fn [db _]
;;    (reaction (:bones/logged-in? @db))))

(re-frame/reg-sub
 :accounts
 (fn [db _]
   (seq (:accounts db))))

(re-frame/reg-sub
 :component/toggle
 (fn [db [_ component-name]]
   (get-in db [:components component-name :show])))

(re-frame/reg-sub
 :components
 (fn [db args]
   (get-in db args)))

(re-frame/reg-sub
 :undos
 (fn [db args]
   (get-in db [:undos])))
