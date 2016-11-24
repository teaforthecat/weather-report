(ns weather-report.components
  (:require-macros [weather-report.macros :refer [button]])
  (:require  [weather-report.subs]
             [weather-report.handlers]
             [reagent.core :as reagent]
             [reagent-forms.core :refer [bind-fields bind init-field]]
             [re-frame.core :refer [dispatch subscribe]]))

(defn small-button [label event]
  (button label event "small" "borderless"))

(defn remove-btn [id]
  [:div.actions
   (small-button "Remove" [:request/command
                     :add-account
                     {:account/xact-id (int id)
                      :account/evo-id nil}])])

(defn account-li [{:keys [:account/xact-id :account/evo-id]}]
  ^{:key xact-id}
  [:tr
   [:td.center xact-id]
   [:td.center  evo-id]
   [:td.center
    [remove-btn xact-id]]])

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
        (map account-li @accounts)]])))

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
  (let [f (reagent/atom {})
        errors (reagent/atom {})]
    [(fn []
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
              (button "Cancel" [:component/hide :add-account #(reset! f {})])
              (button "Submit" [:request/command :add-account @f {:errors errors :form f}])
              ]))]))

(defn add-account []
  (toggle [:component/toggle :add-account]
          (add-account-form)
          (button "Add Account" [:component/show :add-account])))

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
                         (:message @errors)] ;; placeholder
                        [
                         (field :username "Username")
                         (field :password "Password" :type :password)
                         ]
                        [:div.buttons
                         (button "Cancel" [ :component/transition :login-form #(assoc % :show false :form {} :errors {})])
                         (button "Submit" [:request/login @f {:errors errors}])
                         ])]]])])
          (button "Login" [:component/transition :login-form #(assoc % :show true)])))

(defn login []
  (toggle [:bones/logged-in?]
          (small-button "Logout" [:request/logout])
          [login-form]))
