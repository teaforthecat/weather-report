(ns weather-report.views
  (:require [re-frame.core :refer [dispatch subscribe]]
            [weather-report.components :as c]))

;; todo: make dynamic
(def language :en)

(def dictionary
  {:en
   {:home "Home"
    :accounts "Accounts"
    :weather-report "Weather Report"}})

(defn translate [k]
  (get-in dictionary [language k] "nothing to translate"))

(defn navigation []
  [:div#primary-navigation
   [:div.sr-primary-nav
    (translate :weather-report)]])

(defn accounts-view []
  (c/toggle [:bones/logged-in?]
          [:div.accounts-view
           [c/add-account]
           [c/accounts-list]]
          [:div.accounts-view "-"]))

;; layouts
(defmulti layout identity)

(defmethod layout :application [layout-name]
  (fn [& body]
    [:div#wrap
     [navigation]
     [:div#main
      [:div#header_container
       [:div.main-container
        [:div#header.pure-g
         [:div.pure-u-5-8.breadcrumb-title
          [:ul#breadcrumbs]
          [:h1 (translate :accounts)]]
         [:div.pure-u-3-8
          [:div#admin-nav.sr-fload-right
           [:ul.navbar
            [:li.separator [c/undo-button]]
            [:li.separator [c/user-info]]
            [:li.separator.last
             [c/login]]]]]]]]
      [:div.flexer
       (into [:div.main-container]
             body)]]]))

(defn main-panel []
  (let [logged-in (subscribe [:bones/logged-in?])]
    (fn []
      [(layout :application)
       accounts-view])))
