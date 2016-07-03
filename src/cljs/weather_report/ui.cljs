(ns weather-report.ui
  (:require [re-frame.core :refer [dispatch subscribe]]))

(defn account-li [{:keys [:account/xact-id :account/evo-id]}]
  [:li
   [:span xact-id]
   [:span "=>"]
   [:span evo-id]])

(defn accounts-list []
  (let [accounts (subscribe [:accounts])]
    (fn []
      [:div.accounts-list
       (into [:ul] (map account-li @accounts))]
      ))
  )

(comment
  (println-str @re-frame.db/app-db))
