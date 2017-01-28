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
      {:dispatch [:editable [:editable :accounts id :inputs message]
                            [:editable :accounts id :state :editing :evo-id false]]}
      ;; deletion
      {:db (update-in db [:editable :accounts] dissoc id)})))

(re-frame.core/reg-fx :log println)

(defmethod response/handler [:response/command 200]
  [{:keys [db]} [channel response status tap]]
  (let [{:keys [e-scope args command]} tap
        [_ e-type identifier] e-scope]
    (cond
      (= identifier :new)
      {:dispatch (h/editable-response e-type identifier response)}
      (= command :accounts/update)
      ;; bug in e-scope
      ;; :response/command
      ;; 1
      ;; {:args {:evo-id 88866, :xact-id 777}, :topic "accounts", :partition 0, :offset 47}
      ;; 2200
      ;; 3{:command :accounts/update, :args {:evo-id 88866, :xact-id 777}, :e-scope [:editable :accounts]}
      ;; {:dispatch [:editable e-type identifier :state :editing :evo-id false]}
      {:log {:message "response received, waiting for events..."}}
      (= command :accounts/delete)
      {:log {:message "response received, waiting for events..."}})))



(comment
  @re-frame.db/app-db

  ((main-panel))

  (accounts-view)


  )
