(ns weather-report.components
  (:require  [weather-report.subs]
             [weather-report.handlers]
             [reagent.core :as reagent]
             [bones.editable :as e]
             [clojure.string :refer [join]]
             [reagent-forms.core :refer [bind-fields bind init-field]]
             [re-frame.core :refer [dispatch subscribe]]))


(defn toggle
  "usage:
  subscribe to a value, and return the corresponding component
  two form:
  [toggle [:x :y :z]
    [when-truthy-component]
    [when-falsey-component]]

  three form
  [toggle [:x :y :z]
    [when-nil-component]
    [when-truthy-component]
    [when-false-component]]"
  ([subs true-form false-form]
   (let [t (subscribe subs)]
     (fn [subs true-form false-form]
       (if @t
         true-form
         false-form))))
  ([subs nil-form true-form false-form]
   (let [t (subscribe subs)]
     (fn [subs nil-form true-form false-form]
       (let [v @t]
         (cond
           (nil? v) nil-form
           (false? v) false-form
           v true-form))))))

(defn button [label event {:as attrs :or {}}]
  (let [with-class (update attrs :class str " sr-button")
        attributes (assoc with-class :on-click #(dispatch event))]
    [:button attributes label]))

(defn small-button [label event & {:as attrs}]
  (button label event (update attrs :class str " small borderless" )))

(defn undo []
  [:undo/do-undo])

(defn command [cmd args & {:as tap}]
  [:request/command cmd args tap])

(defn undo-button []
  (let [logged-in? (subscribe [:bones/logged-in?])
        undos (subscribe [:undos])]
    (fn []
      (if (and @logged-in? (not-empty @undos))
        [:div.actions
         (small-button "Undo" (undo))]))))

(defn remove-btn [id]
  [:div.actions
   ;; to delete evo-id is to invoke log compaction is to delete the fusion
   (small-button "Remove" (command :accounts/delete {:evo-id nil :xact-id id}))])

(defn account-row [id]
  (let [{:keys [inputs state reset save edit]} (e/form :accounts id)]
    (fn [id]
      [:tr
       [toggle
        [:editable :accounts id :state :editing :xact-id]
        [:td.center
         [e/input :accounts id :xact-id
          :type "text"
          :on-blur reset
          :on-key-down (e/detect-controls {:enter save
                                           :escape reset})]]
        [:td.center
         {:on-double-click (edit :xact-id)}
         (inputs :xact-id)]]
       [toggle
        [:editable :accounts id :state :editing :evo-id]
        [:td.center
         [e/input :accounts id :evo-id
          :type "text"
          :on-blur reset
          :on-key-down (e/detect-controls {:enter save
                                           :escape reset})]]
        [:td.center
         {:on-double-click (edit :evo-id)}
         (inputs :evo-id)]]
       [:td.center
        [remove-btn (inputs :xact-id)]]])))

(def accounts-empty
  [:tr
   [:td.center]
   [:td.center "no accounts"]
   [:td.center]])

(defn accounts-table []
  (let [accounts (subscribe [:editable :accounts])]
    (fn []
      [:table.accounts-list.data-table
       [:thead
        [:tr
         [:th "Xact ID"]
         [:th "Evo ID"]
         [:th "controls"]]]
       (if (empty? @accounts)
         [:tbody
          accounts-empty]
         (into [:tbody]
               (map (fn [id]
                      ^{:key id}
                      [account-row id])
                    (keys @accounts))))])))

(defn account-fusion-form []
  (let [{:keys [reset]} (e/form :accounts :new)]
    (fn []
      [:div.sr-modal
       [:div.sr-modal-dialog
        [:div.sr-modal-header
         [:div.sr-modal-title
          "Fuse Accounts"]]
        [:div.sr-modal-body
         [:ol.form
          [:div
           [:div.fields
            [:li.form-group
             [:label.control-label {:for :xact-id} "XactId"]
             [e/input :accounts :new :xact-id
              :class "short form-control"
              :id :xact-id
              :type "text"]]
            [:li.form-group
             [:label.control-label {:for :evo-id} "EvoId"]
             [e/input :accounts :new :evo-id
              :class "short form-control"
              :type "text"]]
            [:div.buttons
             [:button.sr-button {:on-click reset}
              "Cancel"]
             [:button.sr-button {:on-click #(dispatch [:request/command :accounts/upsert :new])}
              "Submit"]]]]]]]])))

(defn add-account []
  [toggle [:editable :accounts :new :state :show]
   [account-fusion-form]
   (button "New Account Fusion" [:editable :accounts :new :state :show true] {})])

(defn login-form []
  [toggle [:editable :login :new :state :show]
   [(let [{:keys [reset save errors]} (e/form :login :new)]
      (fn []
        [:div.sr-modal
         [:div.sr-modal-dialog
          [:div.sr-modal-header
           [:div.sr-modal-title
            "Login"]]
          [:div.sr-modal-body
           [:ol.form
            [:span
             (errors :message)]
            [:div.fields
             [:li.form-group
              [:label.control-label {:for :username} "Username"]
              [e/input :login :new :username
               :class "short form-control"
               :id :username
               :type "text"]]
             [:li.form-group
              [:label.control-label {:for :password} "Password"]
              [e/input :login :new :password
               :class "short form-control"
               :id :password
               :type "password"]]
             [:div.buttons
              [:button.sr-button {:on-click reset}
               "Cancel"]
              [:button.sr-button {:on-click #(dispatch [:request/login :login :new])}
               "Submit"]
              ]]]
           ]]]))]
   [:button.sr-button {:on-click #(dispatch [:editable :login :new :state :show true])}
    "Login"]])

(defn user-info []
  (let [user-info (subscribe [:components :user-info])
        logged-in? (subscribe [:bones/logged-in?])]
    (fn []
      (if (and @logged-in?  @user-info)
        [:span.user-info (str "Hello " (:display-name @user-info))]
        [:span]))))

(defn login []
  [toggle [:bones/logged-in?]
   (small-button "Logout" [:request/logout :logout :now])
   [login-form]])
