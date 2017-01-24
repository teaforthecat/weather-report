(ns weather-report.views
  (:require [re-frame.core :refer [dispatch subscribe]]
            [weather-report.components :as c]
            [bones.editable.response :as response]
            [bones.editable.helpers :as h]
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
  [c/toggle [:bones/logged-in?]
   [:div.accounts-view
    (c/add-account)
    [c/accounts-table]]
   [:div.accounts-view "hi"]])

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

(defmethod response/handler :event/message
  [{:keys [db]} [channel message]]
  (let [id (:xact-id message)
        evo-id (:evo-id message)]
    (if evo-id
      {:db (update-in db [:editable :accounts id :inputs] merge message)}
      ;; deletion
      {:db (update-in db [:editable :accounts] dissoc id)})))

(defmethod response/handler [:response/command 200]
  [{:keys [db]} [channel response status tap]]
  (let [{:keys [e-scope]} tap
        [_ e-type identifier] e-scope]
    (if (= identifier :new)
      {:dispatch (h/editable-response e-type identifier response)}
      {:log {:message "response received, waiting for events..."}})))



(comment
  @re-frame.db/app-db

  ((main-panel))

  (accounts-view)


  )
