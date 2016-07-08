(ns weather-report.views
  (:require [re-frame.core :as re-frame]
            [weather-report.forms :as forms]
            [weather-report.ui :as ui]))


;; home

(defn home-panel []
  (let [name (re-frame/subscribe [:name])
        logged-in (re-frame/subscribe [:bones/logged-in?])]
    (fn []
      [:div (str "Hello from " @name ". This is the Home Page.")
       [:div [:a {:href "#/about"} "go to About Page"]]
       [forms/login]
       (if @logged-in
         [:div.accounts-view
          [forms/add-account]
          [ui/accounts-list]])])))


;; about

(defn about-panel []
  (fn []
    [:div "This is the About Page."
     [:div [:a {:href "#/"} "go to Home Page"]]]))


;; main

(defmulti panels identity)
(defmethod panels :home-panel [] [home-panel])
(defmethod panels :about-panel [] [about-panel])
(defmethod panels :default [] [:div])

(defn show-panel
  [panel-name]
  [panels panel-name])

(defn main-panel []
  (let [active-panel (re-frame/subscribe [:active-panel])]
    (fn []
      [show-panel @active-panel])))
