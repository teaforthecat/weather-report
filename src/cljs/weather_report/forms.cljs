(ns weather-report.forms
  (:require [bones.re-frame.forms :as bones]
            [schema.core :as s]
            [reagent.ratom :as ratom]
            [reagent.core :as reagent]
            [reagent-forms.core :refer [bind-fields bind init-field]]
            [re-frame.core :refer [subscribe dispatch]]))



(defn login []
  ;; login-url
  ;; fields (username, password)
  ;; button text
  (bones/login-form "/api/login")
  )

(def account-spec
  {:account/xact-id s/Int
   :account/evo-id (s/maybe s/Int)})

(def account-error-messages
    {:account/xact-id {:container "has-error"
                       :aria      "is required"}
     :account/evo-id {:container "has-error"
                      :aria      "must be a number or empty"}})

(defn add-account []
  (let [default-form {:enabled? false
                      :account/xact-id nil
                      :account/evo-id nil}
        form (reagent/atom default-form)
        validator (bones/form-validator account-spec account-error-messages)]
    (fn []
      (if (:enabled? @form)
        [:div.form
         [:h3 "Add Account"]
         [bind-fields
          [:div.fields
           (bones/bootstrap-field-with-feedback :account/xact-id "Xact Id" validator
                                                :field :numeric
                                                :type :number)
           (bones/bootstrap-field-with-feedback :account/evo-id "Evo Id" validator
                                                :field :numeric
                                                :type :number)]
          form
          (fn [id value doc]
            (println (pr-str doc))
            (validator doc))]
         [bones/cancel "Cancel" form default-form]
         [bones/submit "Submit" :add-account form default-form]
         ]
        [:button {:on-click #(swap! form assoc :enabled? true)}
         "Add Account"]))))
