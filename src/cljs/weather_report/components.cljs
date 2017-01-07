(ns weather-report.components
  (:require  [weather-report.subs]
             [weather-report.handlers]
             [reagent.core :as reagent]
             [bones.editable :as e]
             [clojure.string :refer [join]]
             [reagent-forms.core :refer [bind-fields bind init-field]]
             [re-frame.core :refer [dispatch subscribe]]))

(defn button [label event {:as attrs :or {}}]
  (let [with-class (update attrs :class str " sr-button")
        attributes (assoc with-class :on-click #(dispatch event))]
    [:button attributes label]))

(defn small-button [label event & {:as attrs}]
  (button label event (update attrs :class str " small borderless" )))

(defn undo []
  [:undo/do-undo])

(defn transition [cmp & attrs]
  [:component/transition cmp #(apply assoc % attrs)])

(defn command [cmd args & {:as tap}]
  [:request/command cmd args tap])

(defn submit [cmd form errors]
  (command cmd @form :errors errors :form form))

(defn cancel [cmp]
  (transition cmp :show false :form {} :errors {}))

(defn undo-button []
  (let [logged-in? (subscribe [:bones/logged-in?])
        undos (subscribe [:undos])]
    (fn []
      (if (and @logged-in? (not-empty @undos))
        [:div.actions
         (small-button "Undo" (undo))]))))

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
  ;; call it right away so it can be used as the top definition of a component,
  ;; and that component can be used as a symbol such as: [toggled-thing]
  ((let [t (subscribe subs)]
     (fn []
       (if @t
         true-form
         false-form)))))

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
             (button "Submit" (submit :add-account f errors) {})
             ]))))

(defn add-account []
  (toggle [:components :add-account :show]
          [add-account-form]
          (button "Add Account" (transition :add-account :show true) {})))

(defn login-form []
  (toggle [:editable :login :new :state :show]
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
                      :id :username]]
                    [:li.form-group
                     [:label.control-label {:for :password} "Password"]
                     [e/input :login :new :password
                      :class "short form-control"
                      :id :password
                      :type "password"]]
                    [:div.buttons
                     [:button {:on-click reset}
                      "Cancel"]
                     [:button {:on-click #(dispatch [:request/login :login :new])}
                      "Submit"]
                     ]]]
                  ]]]))]
          [:button {:on-click #(dispatch [:editable :login :new :state :show true])}
           "Login"]))

(defn user-info []
  (let [user-info (subscribe [:components :user-info])
        logged-in? (subscribe [:bones/logged-in?])]
    (fn []
      (if (and @logged-in?  @user-info)
        [:span.user-info (str "Hello " (:display-name @user-info))]
        [:span]))))

(defn login []
  (toggle [:bones/logged-in?]
          (small-button "Logout" [:request/logout :logout :now])
          [login-form]))
