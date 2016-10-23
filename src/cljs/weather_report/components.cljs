(ns weather-report.components
  (:require  [weather-report.subs]
             [weather-report.handlers]
             [reagent.core :as reagent]
             [reagent-forms.core :refer [bind-fields bind init-field]]
             [re-frame.core :refer [dispatch subscribe]]))

(defn remove-btn [id]
  [:div.actions
   [:button.remove {:on-click #(dispatch [:request/command
                                          :add-account
                                          {:account/xact-id (int id)
                                           :account/evo-id nil}])}
    "Remove"]])

(defn account-li [{:keys [:account/xact-id :account/evo-id]}]
  [:li
   [:span xact-id]
   [:span " => "]
   [:span evo-id]
   [remove-btn xact-id]])

(defn accounts-list []
  (let [accounts (subscribe [:accounts])]
    (fn []
      [:div.accounts-list
       (into [:ul] (map account-li @accounts))])))

(defn toggle [subs true-form false-form]
  (let [t (subscribe subs)]
    (fn []
      (if @t
        true-form
        false-form))))

(defn form [f header fields buttons]
  [:div.form
   header
   [bind-fields
    (into [:div.fields] fields)
    f]
   buttons])

(defn field [id label & {:as opts}]
  [:div.form-group.has-feedback {:field :container}

   ;; the label can change
   [:label.control-label {:for id
                          :placeholder label
                          :id [:errors id :label]
                          :field :label}]
   ;; the input finally
   [:input.form-control {:id id
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
  (let [f (reagent/atom {})]
    (form f
          [:h3 "Add Account"]
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
           [:button {:on-click #(do (dispatch [:component/hide :add-account])
                                    (reset! f {}))}
            "Cancel"]
           [:button {:on-click #(dispatch [:request/command :add-account @f])}
            "Submit"]])))

(defn add-account []
  (toggle [:component/toggle :add-account]
          (add-account-form)
          [:button {:on-click #(dispatch [:component/show :add-account])}
           "Add Account"]))

(defn login-form []
  (toggle [:component/toggle :login-form]
          (let [f (reagent/atom {})]
            (form f
                  [:h3 "Login Form"]
                  [
                   (field :username "Username")
                   (field :password "Password" :type :password)
                   ]
                  [:div.buttons
                   [:button {:on-click #(do (dispatch [:component/hide :login-form])
                                            (reset! f {}))}
                    "Cancel"]
                   [:button {:on-click #(dispatch [:request/login @f])}
                    "Submit"]]))
          [:button {:on-click #(dispatch [:component/show :login-form])}
           "Login"]))

(defn login []
  (toggle [:bones/logged-in?]
          [:button {:on-click #(dispatch [:request/logout])}
           "Logout"]
          [login-form]))
