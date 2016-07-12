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
(defmethod command :default [command]
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
      )))

(defn resolve-command [command-name]
  (let [nmspc (namespace command-name)
        f     (name command-name)]
    (if nmspc
      (ns-resolve (symbol nmspc) (symbol f))
      (resolve (symbol f)))))

;; WIP
(defn register-command [command-name schema & forms]
  (add-command [command-name schema])
  (defmethod command command-name [command]
    (if (empty? forms)
      (if-let [command-fn (resolve-command command-name)]
        (command-fn (:args command))
        (throw "could not resolve function to call"))
      (let [cmd (first forms)] ;; meh, shrug
        (apply cmd (:args command))))))

(defn handle-command [ctx]
  (command (get-in ctx [:parameters :body])))

(defn command-resource [opts]
  (resource {:id "cqrs/command"
             :description "post commands"
             ;;:summary …
             :methods {:post {:parameters {:body Command}
                              :response #'handle-command
                              }}
             :produces "application/edn"
             :consumes "application/edn"
             :responses {400 {:produces [{:media-type #{"application/edn"}}]
                              :response (fn [ctx]
                                          (-> ctx :error ex-data :error))}}
             ;;:authentication {…}
             ;;:cors {…}
             ;;:properties {…}
             ;;:custom/other {…}
             }))

(defn cqrs [opts]
  )
