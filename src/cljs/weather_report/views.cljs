(ns weather-report.views
  (:require [re-frame.core :refer [dispatch subscribe]]
            [weather-report.components :as c]))


;; home

(defn home-panel []
  (let [name (subscribe [:name])
        logged-in (subscribe [:bones/logged-in?])]
    (fn []
      [:div
       [:div (str "Hello from " @name ". This is the Home Page.")]
       [:div (str "Your connection is ?")]
       [:div [:a {:href "#/about"} "go to About Page"]]
       [c/login]
       (if @logged-in
         [:div.accounts-view
          [c/add-account]
          [c/accounts-list]])])))


;; about

(defn about-panel []
  (fn []
    [:div "This is the About Page."
     [:div [:a {:href "#/"} "go to Home Page"]]]))


;; main

(defmulti panel identity)
(defmethod panel :home-panel [] [home-panel])
(defmethod panel :about-panel [] [about-panel])
(defmethod panel :default [] [:div])

(defn show-panel
  [panel-name]
  [panel panel-name])

(defn main-panel []
  (let [active-panel (subscribe [:active-panel])]
    (fn []
      [show-panel @active-panel])))
