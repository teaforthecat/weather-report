(ns bones.http.handlers
  (:require [yada.yada :refer [resource yada]]
            [bidi.ring :refer [make-handler]]
            [clojure.string :as string]
            [byte-streams :refer [def-conversion]]
            [schema.experimental.abstract-map :as abstract-map]
            [schema.coerce :as sc]
            [schema.core :as s]
            ;;ped
            [io.pedestal.http.route :as route]
            [io.pedestal.http.route.definition.table :as table]
            [io.pedestal.http.route.definition :refer [defroutes]]
            [io.pedestal.interceptor :refer [interceptor]]
            [io.pedestal.http.body-params :as body-params]
            [io.pedestal.interceptor.helpers :as helpers]
            [io.pedestal.interceptor.error :refer [error-dispatch]]
            [ring.util.response :as ring-resp]))

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
                      (string/replace "/" "-")
                      (string/replace "." "-")
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

(add-command [:ping {:a s/Int}])

(defmethod command :ping [command]
  (print-str command)
  "pong")

(defn get-req-body [ctx]
  (-> ctx
      :request
      :edn-params ;; or other?
      ))

(defn get-req-command [ctx]
  (:command (get-req-body ctx)))

(defn command-handler [ctx]
  (command (get-req-command ctx)))

(defn check-command-exists [commands]
  (interceptor {:enter (fn [ctx]
                         (if (some #{(get-req-command)} commands)
                           ctx ;;do nothing
                           (throw (ex-info "command not found"))))}))


(defn check-args-spec [commands]
  (interceptor {:enter (fn [ctx]
                         (s/validate Command (get-req-body ctx)))}))

;; just a short-cut, for it all goes well
(def responser
  (interceptor {:leave (fn [ctx] (update ctx :response ring-resp/response))}))

(defn command-resource [args]
  (defn command-resource [args]
    [:bones/command
     ^:interceptors [(body-params/body-params)
                     (check-command-exists)
                     (check-args-spec)
                     'responser
                     'error-interceptor]
     'command-handler]))
