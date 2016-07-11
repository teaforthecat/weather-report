(ns bones.http.handlers
  (:require [yada.yada :refer [resource]]
            [clojure.string :refer [replace]]
            [byte-streams :refer [def-conversion]]
            [schema.experimental.abstract-map :as abstract-map]
            [schema.coerce :as sc]
            [schema.core :as s]))

(s/defschema Command
  (abstract-map/abstract-map-schema
   :command
   {:args {s/Keyword s/Any}}))

(defmulti command :command)
(defmethod command :default [ctx]
  {:status 400
   :body {:error "command not found"}})

;; todo; macro time
(defn add-command [cmd]
  (let [[command-name schema] cmd]
    (let [varname (-> command-name
                      str
                      (replace "/" "-")
                      (replace "." "-")
                      (subs 1)
                      (str "-schema")
                      (symbol))]
      (abstract-map/extend-schema! Command {:args schema} varname [command-name])
      ) ))

(comment
  (def some-jobs
    {:bones.core/wat {:weight-kg s/Num
                      :name s/Str}
     :bones.core/who {:name s/Str
                      :role s/Str}})

  (map
   add-command
   some-jobs)

  (s/check Command {:command :bones.core/who :args {}})

  (defmethod command (keys some-jobs) [body]
    {:status 200
     :body {:message (str "found command: " (:command body))}})

  ;; hack
  (def-conversion [schema.utils.ValidationError java.lang.String]
    [validation-error]
    (schema.utils/validation-error-explain validation-error))

  )

(defn handle-command [ctx]
  (command (get-in ctx [:parameters :body])))

(defn command-resource [opts]
  (resource    {:id "cqrs/command"
                :description "post commands - the c in cqrs"
  ;;               :summary …
                ;; abstract-map needs to be supported by yada,
                ;; currently a non existent command yields a 500 error
                :methods {:post {:parameters {:body Command}
                                 :response #'handle-command
                                                                 }}
                :produces "application/edn"
                :consumes "application/edn"
                :responses {400 {:produces [{:media-type #{"application/edn"}}]
                                 :response (fn [ctx]
                                             (-> ctx :error ex-data :error))}}
  ;;               :authentication {…}
  ;;               :cors {…}
  ;;               :properties {…}
  ;;               :custom/other {…}
                }))

(defn cqrs [opts]
  )
