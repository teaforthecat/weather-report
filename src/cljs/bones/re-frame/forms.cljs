(ns bones.re-frame.forms
  (:require-macros [reagent-forms.macros :refer [render-element]])
  (:require [schema.core :as s]
            [reagent.ratom :as ratom]
            [reagent.core :as reagent]
            [reagent-forms.core :refer [bind-fields bind init-field]]
            [re-frame.core :refer [subscribe dispatch]]))

(defn connected-status []
  (let [status (subscribe [:connection-status])]
    (fn []
      [:div.connection-status
       (if @status
         [:h2.connected "connected"]
         [:h2.not-connected "not-connected"])])))

(defn submit-button [label command form default-form]
  [:button {:on-click #(dispatch [:request/command
                                  command
                                  (select-keys @form
                                               [:account/xact-id
                                                :account/evo-id])
                                  {:form form
                                   :default-form default-form}])
            :disabled (if-not (empty? (:errors @form)) "disabled")}
   label])

(defn cancel-button [label form-ratom default-form]
  [:button {:on-click #(reset! form-ratom default-form)}
   "Cancel"])

(defn logout-button [label form-ratom]
  [:button {:on-click #(dispatch [:logout form-ratom])}
   label])

(defn login-button [label form-ratom]
  [:button {:on-click #(swap! form-ratom assoc :enabled? true)}
   label])


;;example:
;; (s/optional-key :email) (s/maybe
;;                             (s/pred #(string? (re-matches #".+@.+\..+" %)) :valid-email))

(def validators
  {:username s/Str
   :password s/Str
   })

(def error_messages
  {:email {:container "has-error"
           :glyphicon "glyphicon-remove"
           :aria      "Email is invalid"}
   :password {:container "has-error"
              :aria "Password is required"}})

(defn form-validator [validators error_messages]
  (fn [doc]
    ;; this can be used two ways
    ;; "doc" for a `bind-fields' event processor
    ;; "@doc" for reagent-form container `:valid?' validator
    (let [real-doc (if (= PersistentArrayMap (type doc)) doc @doc)
          result (s/check validators real-doc)]
      (if (or (nil? result) (instance? PersistentArrayMap result))
        (assoc doc :errors (select-keys error_messages (keys result)))
        (throw (ex-info "invalid schema" {:validators validators}))))))

(defn field [id label valid-fn & {:as opts}]
  (let [aria-id [:errors id :aria]
        validation #(get-in (valid-fn %) [:errors id])]
    [:div.form-group.has-feedback {:field :container
                                   :valid? #(:container (validation %))}

     ;; the label can change
     [:label.control-label {:for id
                            :placeholder label
                            :id [:errors id :label]
                            :field :label}]
     ;; the input finally
     [:input.form-control {:id id
                           :aria-describedby aria-id
                           :type (or (:type opts) :text)
                           :field (or (:field opts) :text)}]
     ;; use container to change class
     [:span.glyphicon.form-control-feedback {:field :container
                                             :valid?  #(:glyphicon (validation %))
                                             :aria-hidden true}]
     ;; use label to change content
     [:span.sr-only {:id aria-id
                     :field :label}]
     ]))


(defn login-form []
  (let [default-form {:enabled? false}
        form (reagent/atom {})
        logged-in? (subscribe [:bones/logged-in?])
        validator (form-validator validators error_messages)]
    (fn []
      (if (:enabled? @form)
        [:div.form
         [:h3 "Login"]
         [bind-fields
          [:div.fields
           (field :username "Username" validator)
           (field :password "Password" validator :type :password)]
          form
          (fn [id value doc]
            (validator doc))]
         [cancel-button "Cancel" form default-form]
         [:button {:on-click #(dispatch [:request/login
                                         (select-keys @form [:username :password])
                                         {:form form}])}
          "Submit"]]
        (if @logged-in?
          ;; [logout-button "Logout" form default-form]
          [:button {:on-click #(dispatch [:request/logout form])} "Logout"]
          [:button {:on-click #(swap! form assoc :enabled? true)
                    :disabled (if-not (empty? (:errors @form)) "disabled")}
           "Login"])))))
