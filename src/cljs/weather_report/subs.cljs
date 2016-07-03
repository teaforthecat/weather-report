(ns weather-report.subs
    (:require-macros [reagent.ratom :refer [reaction]])
    (:require [re-frame.core :as re-frame]))

(re-frame/register-sub
 :name
 (fn [db]
   (reaction (:name @db))))

(re-frame/register-sub
 :active-panel
 (fn [db _]
   (reaction (:active-panel @db))))

(re-frame/register-sub
 :bones/logged-in?
 (fn [db _]
   (reaction (:bones/logged-in? @db))))

(re-frame/register-sub
 :accounts
 (fn [db _]
   (reaction (:accounts @db))))
