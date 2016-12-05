(ns weather-report.views
  (:require [re-frame.core :refer [dispatch subscribe]]
            [weather-report.components :as c]))

(def language :en)

(def dictionary
  {:home "Home"
   :cities "Cities"
   :about "About"})

(defn translate [k]
  (get dictionary k))

(defprotocol Render
  (render [thing] [thing args]))

(extend-protocol Render
  Keyword
  (render [thing]
    (translate thing))
  MultiFn
  (render [thing args]
    (into [thing] args))
  function
  (render [thing args]
    (into [thing] args)))

(comment
  (render navigation [:x :y :z])
  (render :cites))

(defn navigation []
  [:div#primary-navigation
   [:div.sr-primary-nav
    [:a#logo {:href "#/"}]
    [:nav {:role "navigation" :aria-label "Main Navigation"}
     [:a {:href "#/"} "Weather Report"]
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
       (render navigation)
       [:div#main
        [:div#header_container
         [:div.main-container
          [:div#header.pure-g
           [:div.pure-u-5-8.breadcrumb-title
            [:ul#breadcrumbs]
            [:h1 (render @active-panel)]]
           [:div.pure-u-3-8
            [:div#admin-nav.sr-fload-right
             [:ul.navbar
              [:li.separator (render c/undo-button)]
              [:li.separator (render c/user-info)]
              [:li.separator.last
               (render c/login)]]]]]]]
        [:div.flexer
         (into [:div.main-container]
               (rest body))]]])))

;; home

(defn home-panel []
  (let [logged-in (subscribe [:bones/logged-in?])]
    (fn []
      (render
       layout
       [:application
        (if @logged-in
          [:div.accounts-view
           (render c/add-account)
           (render c/accounts-list)])]))))

(defn cities-panel []
  (let [logged-in (subscribe [:bones/logged-in?])]
    (fn []
      (render
       layout
       [:application
        (if @logged-in
          [:div.cities-view
           (render c/add-city)
           (render c/cities-list)])]))))

;; about

(defn about-panel []
  (fn []
    [:div "This is the About Page."
     [:div [:a {:href "#/"} "go to Home Page"]]]))


;; main

(defmulti panel identity)
(defmethod panel :home [] [home-panel])
(defmethod panel :cities [] [cities-panel])
(defmethod panel :about [] [about-panel])
(defmethod panel :default [] [:div])

(defn show-panel [panel-name]
  [panel panel-name])

(defn main-panel []
  (let [active-panel (subscribe [:active-panel])]
    (fn []
      [show-panel @active-panel])))
