(ns weather-report.components
  (:require  [weather-report.subs]
             [weather-report.handlers]
             [weather-report.accounts :as accounts]
             [reagent.core :as reagent]
             [bones.editable :as e]
             [clojure.string :refer [join]]
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

(defn command [cmd args & {:as tap}]
  [:request/command cmd args tap])

(defn remove-btn [id]
  [:div.actions
   ;; to delete evo-id is to invoke log compaction is to delete the fusion
   (small-button "Remove" (command :accounts/delete {:evo-id nil :xact-id id}))])

(defn account-row [id]
  (let [{:keys [inputs state reset save edit]} (e/form :accounts id)]
    (fn [id]
      [:tr
       [:td.center (inputs :xact-id)]
       [toggle
        [:editable :accounts id :state :editing :evo-id]
        ;; when :editing :evo-id
        [:td.center
         [e/input :accounts id :evo-id
          :type "text"
          :on-blur reset
          :on-key-down (e/detect-controls {:enter save
                                           :escape reset})]]
        ;; when not :editing :evo-id
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

;; start a sketch of what an error reporting form helper might look like

(def humanize
  {::accounts/xact-id "XactId is not an integer"
   ::accounts/evo-id  "EvoId is not an integer"
   })

(defn extract-problems [errors]
  (get-in errors [:explain-data :cljs.spec/problems]))

(defn problem-attributes
  "the tail of the spec"
  [errors]
  (set (map (comp last :via) (extract-problems errors))))

(defn translate-problems [errors]
  (map humanize
       (problem-attributes errors)))

;; end sketch

(defn modal [&{:keys [fields title errors cancel submit]}]
  [:div.sr-modal
   [:div.sr-modal-dialog
    [:div.sr-modal-header
     [:div.sr-modal-title
      title]]
    [:div.sr-modal-body
     [:ol.form
      [:li.errors
       [:span
        (or (errors :message)
            (into [:ul]
                  (mapv (partial conj [:li.error-message])
                        (translate-problems (errors)))))
        ]]
      (into [:div.fields] fields)
      [:div.buttons
       [:button.sr-button {:on-click (:on-click cancel)}
        (:label cancel "Cancel")]
       [:button.sr-button {:on-click (:on-click submit)}
        (:label submit "Submit")]]]]]])

(defn account-fusion-form []
  (let [{:keys [reset errors]} (e/form :accounts :new)]
    (modal
     :title "Fuse Accounts"
     :errors errors
     :cancel {:on-click reset
              :label "Cancel"}
     :submit {:on-click #(dispatch [:request/command :accounts/upsert :new])
              :label "Submit"}
     :fields [
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
              ])))

(defn login-modal []
  (let [{:keys [reset errors]} (e/form :login :new)]
    (modal
     :title "Login"
     :errors errors
     :cancel {:on-click reset
              :label "Cancel"}
     :submit {:on-click #(dispatch [:request/login :login :new])
              :label "Submit"}
     :fields [
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
                :type "password"]]])))

(defn login-form []
  [toggle [:editable :login :new :state :show]
   [login-modal]
   [:button.sr-button {:on-click #(dispatch [:editable :login :new :state :show true])}
    "Login"]])

(defn login []
  [toggle [:bones/logged-in?]
   (small-button "Logout" [:request/logout :logout :now])
   [login-form]])

(defn add-account []
  [toggle [:editable :accounts :new :state :show]
   [account-fusion-form]
   (button "New Account Fusion" [:editable :accounts :new :state :show true] {})])

(defn user-info []
  (let [user-info (subscribe [:components :user-info])
        logged-in? (subscribe [:bones/logged-in?])]
    (fn []
      (if (and @logged-in?  @user-info)
        [:span.user-info (str "Hello " (:display-name @user-info))]
        [:span]))))
