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
            [io.pedestal.http.content-negotiation :as cn]
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

;; (def command-handler
;;   (helpers/handler (fn [ctx]
;;                      (command (get-req-command ctx)))))

(defn command-handler [req]
  (command (:body-params req)))

(defn check-command-exists []
  (let [commands (-> Command abstract-map/sub-schemas keys)]
    (interceptor {:enter (fn [ctx]
                           (if (some #{(get-req-command ctx)} commands)
                             ctx ;;do nothing
                             (throw (ex-info "command not found"))))})))

(defn check-args-spec []
  (interceptor {:name :bones/check-args-spec
                :enter (fn [ctx]
                         (s/validate Command (get-req-body ctx)))}))

(defn respond-with [body content-type]
  (-> body
      ring-resp/response
      (ring-resp/content-type content-type)))

(defmulti render #(get-in % [:request :accept :field]))
(defmethod render :default ;; both? "application/edn"
  [ctx]
  (update-in ctx [:response] #(respond-with % "application/edn")))

(def renderer
  (interceptor {:name :bones/renderer
                :leave (fn [ctx] (render ctx))}))

(defn conform-body-params
  "since there should only be one type of params, stuff them into a single
  key on the request :body-params for ease of use
  - I wish the body-params helper did this"
  [ctx]
  (let [req (:request ctx)]
    (assoc-in ctx [:request :body-params] (or (:edn-params req)
                                              (:json-params req)
                                              (:transit-params req)
                                              (:form-params req)))))

(def body-params
  (interceptor {:name :bones/body-params
                :enter conform-body-params}))

;; need to register exceptions here
(def error-interceptor
  (error-dispatch [ctx ex]
                  [{:interceptor :bones/check-command-exists}]
                  (assoc ctx :response (ring-resp/not-found "Command Not Found"))
                  :else
                  (assoc ctx :io.pedestal.interceptor.chain/error ex)))

(defn command-resource [args]
  [:bones/command
   ;; need to use namespace in so this can be called from a macro (defroutes)
   ^:interceptors [(cn/negotiate-content ["application/edn"])
                   (body-params/body-params)
                   'bones.http.handlers/body-params
                   (bones.http.handlers/check-command-exists)
                   ;; (bones.http.handlers/check-args-spec)
                   'bones.http.handlers/renderer
                   ;; 'bones.http.handlers/error-interceptor
                   ]
   ;;todo resolvable symbol here
   ;; ::command-handler
   'bones.http.handlers/command-handler])
