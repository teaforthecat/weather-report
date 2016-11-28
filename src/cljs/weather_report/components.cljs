(ns weather-report.components
  (:require  [weather-report.subs]
             [weather-report.handlers]
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

(defn transition [cmp & attrs]
  [:component/transition cmp #(apply assoc % attrs)])

(defn command [cmd args & {:as tap}]
  [:request/command cmd args tap])

(defn submit [cmd form errors]
  (command cmd @form :errors errors :form form))

(defn cancel [cmp]
  (transition cmp :show false :form {} :errors {}))

(defn remove-btn [id]
  [:div.actions
   (small-button "Remove" (command :add-account ;; nil evo-id removes
                                   {:account/xact-id (int id)
                                    :account/evo-id nil}))])

(defn account-li [{:keys [:account/xact-id :account/evo-id]}]
  ^{:key xact-id}
  [:tr
   [:td.center xact-id]
   [:td.center  evo-id]
   [:td.center
    [remove-btn xact-id]]])

(def accounts-empty
  [:tr
   [:td.center]
   [:td.center "no accounts"]
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
              (button "Cancel" (cancel :add-account) {})
              ;; the command and component happen to have the same name
              (button "Submit" (submit :add-account f errors) {})
              ]))]))

(defn add-account []
  (toggle [:components :add-account :show]
          (add-account-form)
          (button "Add Account" (transition :add-account :show true) {})))

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
                         (button "Submit" [:request/login @f {:errors errors}] {})
                         ])]]])])
          (button "Login" (transition :login-form :show true) {})))

(defn user-info []
  (let [user-info (subscribe [:components :user-info])]
    (fn []
      (if @user-info
        [:span.user-info (str "Hello " (:display-name @user-info))]
        [:span]))))

(defn login []
  (toggle [:bones/logged-in?]
          (small-button "Logout" [:request/logout])
          [login-form]))
