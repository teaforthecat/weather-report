(ns weather-report.ui
  (:require [re-frame.core :refer [dispatch subscribe]]))

(defn remove-btn [id]
  [:div.actions
   [:button.remove {:on-click #(dispatch [:request/command :add-account {:account/xact-id (int id) :account/evo-id nil}])}
    "Remove"]])

(defn account-li [{:keys [:account/xact-id :account/evo-id]}]
  [:li
   [:span xact-id]
   [:span " => "]
   [:span evo-id]
   [remove-btn xact-id]])

(defn accounts-list []
  (let [accounts (subscribe [:accounts])]
    (fn []
      [:div.accounts-list
       (into [:ul] (map account-li @accounts))])))
