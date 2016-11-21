(ns weather-report.views
  (:require [re-frame.core :refer [dispatch subscribe]]
            [weather-report.components :as c]))


;; home

(defn home-panel []
  (let [name (subscribe [:name])
        logged-in (subscribe [:bones/logged-in?])]
    (fn []
      [:div#wrap
       [:div#primary-navigation
        [:div.sr-primary-nav
         [:a#logo {:href "#/"} "Weather Report"]
         [:nav {:role "navigation" :aria-label "Main Navigation"}
          [:a {:href "#/about"}
           [:i.icon-briefcase]
           [:span "About"]]]]]
       [:div#main
        [:div#header_container
         [:div.main-container
          [:div#header.pure-g
           [:div.pure-u-5-8.breadcrumb-title
            [:ul#breadcrumbs]
            [:h1 "Accounts"]]
           [:div.pure-u-3-8
            [:div#admin-nav.sr-fload-right
             [:ul.navbar
              [:li.last.separator
               [c/login]]]]]]]]
        [:div.flexer
         [:div.main-container
          (if @logged-in
            [:div.accounts-view
             [c/add-account]
             [c/accounts-list]])]]]])))


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
