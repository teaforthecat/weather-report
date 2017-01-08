(ns weather-report.views
  (:require [re-frame.core :refer [dispatch subscribe]]
            [weather-report.components :as c]
            [bones.editable :as e]))

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
  [(c/toggle [:bones/logged-in?]
             [:div.accounts-view
              (c/add-account)
              [c/accounts-table]]
             [:div.accounts-view "hi"])])

;; layouts
(defmulti layout identity)

(defmethod layout :application [layout-name & body]
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
           (c/login)]]]]]]]
    [:div.flexer
     (into [:div.main-container]
           body)]]])

(defn main-panel []
  (fn []
    (layout :application
            (accounts-view))))


(defmethod e/handler :event/message
  [{:keys [db]} [channel message]]
  (println channel)
  (println message)
  ;; you probably mostly want to write to the database here
  ;; and have subscribers reacting to changes
  ;;  {:db (other-multi-method db message) }
  (let [id (:xact-id message)]
    {:db (update-in db [:editable :accounts id :inputs] merge message)}))


(comment

  ((main-panel))

  (accounts-view)


  )
