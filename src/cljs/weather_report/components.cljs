(ns weather-report.components
  (:require  [weather-report.subs]
             [weather-report.handlers]
             [weather-report.db :as db]
             [reagent.core :as reagent]
             [clojure.string :refer [join]]
             [reagent-forms.core :refer [bind-fields bind init-field]]
             [re-frame.core :refer [dispatch subscribe]]))

(defn button [label event {:as attrs :or {}}]
  (let [with-class (update attrs :class str " sr-button")
        attributes (assoc with-class :on-click #(dispatch event))]
    [:button attributes label]))

(defn small-button [label event & {:as attrs}]
  (button label event (update attrs :class str " small borderless" )))

(defn dispatch-input [form attr]
  (fn [e]
    (dispatch [:component/assoc form :inputs attr (-> e .-target .-value)])))

(defn undo []
  [:undo/do-undo])

(defn transition [cmp & attrs]
  [:component/transition cmp #(apply assoc % attrs)])

(defn command [cmd args & {:as tap}]
  [:request/command cmd args tap])

(defn submit [cmp command]
  [:form/submit cmp command])

(defn cancel [cmp]
  (transition cmp :show false :inputs {} :errors {}))

(defn undo-button []
  (let [logged-in? (subscribe [:bones/logged-in?])
        undos (subscribe [:undos])]
    (fn []
      (if (and @logged-in? (not-empty @undos))
        [:div.actions
         (small-button "Undo" (undo))]))))

(defn remove-btn [command-name args]
  [:div.actions
   (small-button "Remove" (command command-name args))])

(defn account-li [{:keys [:account/xact-id :account/evo-id] :as account}]
  ^{:key xact-id}
  [:tr
   [:td.center xact-id]
   [:td.center  evo-id]
   [:td.center
    ;; nil evo-id removes
    [remove-btn :add-account (assoc account :account/evo-id nil)]]])

(defn city-li [{:keys [:city/name :city/temp] :as city}]
  ^{:key (:city/name city)}
  [:tr
   [:td.center (:city/name city)]
   [:td.center temp]
   [:td.center
    [remove-btn :add-city (assoc city :city/temp nil)]]])

(def accounts-empty
  [:tr
   [:td.center]
   [:td.center "no accounts"]
   [:td.center]])

(def cities-empty
  [:tr
   [:td.center]
   [:td.center "no cities"]
   [:td.center]])

(defn accounts-list []
  (let [accounts (subscribe [:accounts])]
    (fn []
      [:table.accounts-list.data-table
       [:thead
        [:tr
         [:th "Xact ID"]
         [:th "Evo ID"]
         [:th "controls"]]]
       [:tbody
        (if (empty? @accounts)
          accounts-empty
          (map account-li @accounts))]])))

(defn cities-list []
  (let [cities (subscribe [:cities])]
    (fn []
      [:table.cities-list.data-table
       [:thead
        [:tr
         [:th "City Name"]
         [:th "Temp"]
         [:th "controls"]]]
       [:tbody
        (if (empty? @cities)
          cities-empty
          (map city-li @cities))]])))

(defn toggle [subs true-form false-form]
  (let [t (subscribe subs)]
    (fn []
      (if @t
        true-form
        false-form))))

(defn form [f header fields buttons]
  [:ol.form
   header
   [bind-fields
    (into [:div.fields] fields)
    f]
   buttons])

(defn field [id label & {:as opts}]
  [:li.form-group.has-feedback {:field :container}

   ;; the label can change
   [:label.control-label {:for id
                          :placeholder label
                          :id [:errors id :label]
                          :field :label}]
   ;; the input finally
   [:input.short.form-control {:id id
                         :aria-describedby [:errors id :aria]
                         :type (or (:type opts) :text)
                         :field (or (:field opts) :text)}]
   ;; use container to change class
   [:span.glyphicon.form-control-feedback {:field :container
                                           :aria-hidden true}]
   ;; use label to change content
   [:span.sr-only {:id [:errors id :aria]
                   :field :label}]
   ])

(defn add-account-form []
  (let [f (subscribe [:components :add-account :form])
        errors (subscribe [:components :add-account :errors])]
    (fn []
      (form f
            [:div
             [:h3 "Add Account"]
             [:p (:message @errors)]]
            [
             (field :account/xact-id
                    "Xact Account ID"
                    :field :numeric
                    :type :number)
             (field :account/evo-id
                    "Evo Account ID"
                    :field :numeric
                    :type :number)
             ]
            [:div.buttons
             (button "Cancel" (cancel :add-account) {})
             ;; the command and component happen to have the same name
             ;; (button "Submit" (submit :add-account f errors) {})
             ]))))

(defn input [f form-id attr & {:as opts :or {:type "text" :label "hi"}}]
  [:li.form-group.has-feedback {:field :container}

   ;; the label can change
   [:label.control-label {:for form-id
                          :id [:errors form-id :label]
                          :field :label} (:label opts)]
   ;; the input finally
   [:input.short.form-control {:id form-id
                               :aria-describedby [:errors form-id :aria]
                               :value (attr @f)
                               :on-change (dispatch-input form-id attr)
                               :type (or (:type opts) :text)
                               :field (or (:field opts) :text)}]
   ;; use container to change class
   [:span.glyphicon.form-control-feedback {:field :container
                                           :aria-hidden true}]
   ;; use label to change content
   [:span.sr-only {:id [:errors form-id :aria]
                   :field :label}]
   ])

(defn add-city-form []
  (let [f (subscribe [:components :city-form :inputs])
        old-form (reagent/atom {})
        errors (subscribe [:components :city-form :errors])]
    (fn []

      [:div
       [:h3 "Add City"]
       [:p (:message @errors)]
       [:ol.form
        [:div.fields
         (input f :city-form :db/name :label "Name")
         (input f :city-form :db/temp :label "Temp")]
        [:div.buttons
         (button "Cancel" (cancel :city-form) {})
         ;; the command and component happen to have the same name
         (button "Submit" (submit :city-form :add-city) {})
         ]]])))

(defn add-account []
  (toggle [:components :add-account :show]
          [add-account-form]
          (button "Add Account" (transition :add-account :show true) {})))

(defn add-city []
  (toggle [:components :city-form :show]
          [add-city-form]
          (button "Add City" (transition :city-form :show true) {})))

(defn login-form []
  (toggle [:components :login-form :show]
          (let [f (subscribe [:components :login-form :form])
                errors (subscribe [:components :login-form :errors])]
            [(fn []
               [:div.sr-modal
                [:div.sr-modal-dialog
                 [:div.sr-modal-header
                  [:div.sr-modal-title
                   "Login"]]
                 [:div.sr-modal-body
                  (form f
                        [:span
                         (:message @errors)]
                        [
                         (field :username "Username")
                         (field :password "Password" :type :password)
                         ]
                        [:div.buttons
                         (button "Cancel" (cancel :login-form) {})
                         ;; (button "Submit" [:request/login @f {:errors errors}] {})
                         ])]]])])
          (button "Login" (transition :login-form :show true) {})))

(defn user-info []
  (let [user-info (subscribe [:components :user-info])
        logged-in? (subscribe [:bones/logged-in?])]
    (fn []
      (if (and @logged-in?  @user-info)
        [:span.user-info (str "Hello " (:display-name @user-info))]
        [:span]))))

(defn login []
  (toggle [:bones/logged-in?]
          (small-button "Logout" [:request/logout])
          [login-form]))
