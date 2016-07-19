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

(add-command [:ping {}])

(defmethod command :ping [command]
  "pong")

(defn get-req-body [ctx]
  (-> ctx
      :request
      :body-params
      ))

(defn get-req-command [ctx]
  (:command (get-req-body ctx)))

(defn command-handler [req]
  (command (:body-params req)))

(defn check-command-exists []
  (let [commands (-> Command abstract-map/sub-schemas keys)]
    (interceptor {:name :bones/check-command-exists
                  :enter (fn [ctx]
                           (let [cmd (get-req-command ctx)]
                             (if (some #{cmd} commands)
                               ctx ;;do nothing
                               (throw (ex-info "command not found"
                                               {:status 401
                                                :message (str "command not found: " cmd)
                                                :available-commands commands})))))})))

(defn check-args-spec []
  (interceptor {:name :bones/check-args-spec
                :enter (fn [ctx]
                         (if-let [error (s/check Command (get-req-body ctx))]
                           (throw (ex-info "args not valid" (assoc error :status 401)))
                           ctx))}))

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

(def body-params
  "shim for the body-params interceptor, stuff the result into a single
  key on the request `body-params' for ease of use"
  (interceptor {:name :bones/body-params
                :enter (fn [ctx]
                         (update ctx
                                 :request (fn [req]
                                            (assoc req :body-params (or (:edn-params req)
                                                                        (:json-params req)
                                                                        (:transit-params req)
                                                                        (:form-params req))))))}))

(def error-responder
  (interceptor {:name :bones/error-responder
                :error (fn [ctx ex]
                         (let [data (-> ex ex-data :exception ex-data)
                               status (or (:status data) 500)]
                           (-> (assoc ctx :response (dissoc data :status)) ;; sets body
                               (render) ;; sets content-type
                               (update :response assoc :status status))))}))

(defn command-resource [args]
  [:bones/command
   ;; need to use namespace so this can be called from a macro (defroutes)
   ^:interceptors ['bones.http.handlers/error-responder ;; must come first
                   (cn/negotiate-content ["application/edn"])
                   (body-params/body-params)
                   'bones.http.handlers/body-params
                   (bones.http.handlers/check-command-exists)
                   (bones.http.handlers/check-args-spec)
                   'bones.http.handlers/renderer
                   ]
   'bones.http.handlers/command-handler])
