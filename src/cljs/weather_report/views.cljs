(ns weather-report.views
  (:require [re-frame.core :refer [dispatch subscribe]]
            [weather-report.components :as c]))

(defn navigation []
  [:div#primary-navigation
   [:div.sr-primary-nav
    ;; [:a#logo {:href "#/"}]
    [:nav {:role "navigation" :aria-label "Main Navigation"}
     [:a {:href "#/"} ""]
     [:a {:href "#/"} "Weather Report"]
     [:a {:href "#/"} ""]
     [:a {:href "#/cities"}
      [:i.icon-briefcase]
      [:span "Cities"]]
     [:a {:href "#/about"}
      [:i.icon-briefcase]
      [:span "About"]]]]])

;; layouts
(defmulti layout identity)

(defmethod layout :application [& body]
  (let [name (subscribe [:name])
        logged-in (subscribe [:bones/logged-in?])
        active-panel (subscribe [:active-panel])]
    (fn []
      [:div#wrap
       [navigation]
       [:div#main
        [:div#header_container
         [:div.main-container
          [:div#header.pure-g
           [:div.pure-u-5-8.breadcrumb-title
            [:ul#breadcrumbs]
            [:h1 (str @active-panel)]]
           [:div.pure-u-3-8
            [:div#admin-nav.sr-fload-right
             [:ul.navbar
              [:li.separator [c/undo-button]]
              [:li.separator [c/user-info]]
              [:li.separator.last
               [c/login]]]]]]]]
        [:div.flexer
         (into [:div.main-container]
               (rest body))]]])))

;; home

(defn home-panel []
  (let [logged-in (subscribe [:bones/logged-in?])]
    (if @logged-in
      [:div.accounts-view
       [c/add-account]
       [c/accounts-list]])))

(defn cities-panel []
  (let []
    (fn []
      [:div "cities"])))

;; about

(defn about-panel []
  (fn []
    [:div "This is the About Page."
     [:div [:a {:href "#/"} "go to Home Page"]]]))


;; main

(defmulti panel identity)
(defmethod panel :home-panel [] [home-panel])
(defmethod panel :cities-panel [] [cities-panel])
(defmethod panel :about-panel [] [about-panel])
(defmethod panel :default [] [:div])

(defn show-panel
  [panel-name]
  [panel panel-name])

(defn main-panel []
  (let [active-panel (subscribe [:active-panel])]
    (fn []
      (layout :application
              [panel @active-panel]))))
