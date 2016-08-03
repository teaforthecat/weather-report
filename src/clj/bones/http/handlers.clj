(ns bones.http.handlers
  (:require [com.stuartsierra.component :as component]
            [clojure.string :as string]
            ;; [byte-streams :refer [def-conversion]]
            [schema.experimental.abstract-map :as abstract-map]
            [schema.coerce :as sc]
            [schema.core :as s]
            ;;ped
            [io.pedestal.http.route :as route]
            [io.pedestal.http.route.definition.table :as table]
            [io.pedestal.http.route.definition :refer [defroutes]]
            [io.pedestal.interceptor :refer [interceptor]]
            [io.pedestal.http.ring-middlewares :as middlewares]
            [io.pedestal.http.content-negotiation :as cn]
            [io.pedestal.http.body-params :as body-params]
            [io.pedestal.interceptor.helpers :as helpers :refer [definterceptor]]
            [io.pedestal.interceptor.error :refer [error-dispatch]]
            [ring.util.response :as ring-resp]
            [ring.middleware.session.cookie :refer [cookie-store]]
            [bones.http.auth :as auth]))

(s/defschema Command
  (abstract-map/abstract-map-schema
   :command
   {:args {s/Keyword s/Any}}))

(defmulti command :command)
;; (defmethod command :default [command req]
;;   {:status 400
;;    :body {:error "command not found"}})

(defn add-command
  "ensure a unique command is created based on a name spaced keyword"
  [command-name schema]
  (let [varname (-> command-name
                    str
                    (string/replace "/" "-")
                    (string/replace "." "-")
                    (subs 1)
                    (str "-schema")
                    (symbol))]
    (abstract-map/extend-schema! Command
                                 {:args schema}
                                 varname
                                 [command-name])))

(defn resolve-command [command-name]
  (let [nmspc (namespace command-name)
        f     (name command-name)]
    (if nmspc
      (ns-resolve (symbol nmspc) (symbol f))
      (resolve (symbol f)))))

(defn register-command
  "the command can have the same name of the function (implicit) - it must also
  have the same namespace as the call of this `register-command' function, or a third
  argument can be given that resolves to a function in another namespace, that
  way the command-name can be different from the function name if desired.

    * resolves a keyword to a function
    * adds a method to `command'
    * adds a schema to `Command'"
  ([command-name schema]
   (register-command command-name schema command-name))
  ([command-name schema explicit-handler]
   (let [command-handler (resolve-command explicit-handler)]
     (if (nil? command-handler)
       (throw (ex-info "could not resolve command to a function" {:command explicit-handler} )))
     (add-command command-name schema)
     (defmethod command command-name [command req]
       (command-handler (:args command) req)))))

;; remove?
(defn echo "built-in sanity check" [args req] args)
;; remove?
(register-command :echo {s/Any s/Any})

(defn registered-commands
  "a bare bones, low-level introspection utility"
  []
  (->> Command
       abstract-map/sub-schemas
       ;; login should probably be filtered out?
       (map (fn [sub]
              (:extended-schema (second sub))))))

(defn body [ctx]
  (-> ctx
      :request
      :body-params
      ))

(defn get-req-command [ctx]
  (:command (body ctx)))

(defn command-handler [req]
  (command (:body-params req) req))

(defn command-list-handler [_]
  {:available-commands (registered-commands)})

(defn login-handler [req]
  (if (= :login (get-in req [:body-params :command]))
    (let [user-data (command (:body-params req) req)]
      (if user-data
        {:session {:identity user-data}
         :token (auth/token {:identity user-data})}
        (throw (ex-info "Invalid Credentials" {:status 422}))))
    (throw (ex-info "Invalid Command" {:status 401}))))

(def identity-interceptor
  ;; sets request :identity to data of decrypted "Token" in the "Authorization" header
  ;; sets request :identity to data of :identity in bones-session cookie
  (helpers/on-request :bones.auth/identity
                      (auth/identity-interceptor)))

(def session
  ;; interceptor
  ;; sets request :session to decrypted data in the bones-session cookie
  ;; sets cookie to encrypted value of data in request :session
  (middlewares/session auth/cookie-opts))


(def check-command-exists
  (interceptor {:name :bones/check-command-exists
                :enter (fn [ctx]
                         (let [commands (-> Command abstract-map/sub-schemas keys)
                               cmd (get-req-command ctx)]
                           (if (some #{cmd} commands)
                             ctx ;;do nothing
                             (throw (ex-info "command not found"
                                             {:status 401
                                              :message (str "command not found: " cmd)
                                              :available-commands commands})))))}))

(def check-args-spec
  (interceptor {:name :bones/check-args-spec
                :enter (fn [ctx]
                         (if-let [error (s/check Command (body ctx))]
                           (throw (ex-info "args not valid" (assoc error :status 401)))
                           ctx))}))

(defn respond-with [response content-type]
  (let [session (:session response)]
    (-> (dissoc response :session) ;;body
        ring-resp/response
        (assoc :session session) ;; new session info for Set-Cookie header
        (ring-resp/content-type content-type))))

(defmulti render
  "render content-types, extensible, relies on io.pedestal.http.content-negotiation.
  dispatch-val will be a valid content-type string
  recommended to call (respond-with rendered dispatch-val)"
  #(get-in % [:request :accept :field]))

(defmethod render :default ;; both? "application/edn" configurable?
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
                                            (assoc req
                                                   :body-params (or (:edn-params req)
                                                                    (:json-params req)
                                                                    (:transit-params req)
                                                                    (:form-params req))))))}))

(def error-responder
  ;; get status of the nested(thrown) exception's ex-data for specialized http responses
  (interceptor {:name :bones/error-responder
                :error (fn [ctx ex]
                         (let [exception (-> ex ex-data :exception) ;; nested exception
                               data (ex-data exception)
                               status (or (:status data) 500)
                               resp {:message (.getMessage exception)}
                               response (if (empty? (dissoc data :status))
                                          resp
                                          (assoc resp :data (dissoc data :status)))]
                           (-> (assoc ctx :response response) ;; sets body
                               (render) ;; sets content-type
                               (update :response assoc :status status))))}))

(defn get-resource-interceptors []
  ^:interceptors
  ['bones.http.handlers/error-responder ;; must come first
   (cn/negotiate-content ["application/edn"])
   'bones.http.handlers/renderer])

(defn command-resource
  "# post request handler
    * uses `Command' schema for validation
    * requires exclusively `:command' and `:args' keys in post body
    * hands off `:args' of post body to `:command'
    * commands are registered with `register-command'"
  [conf]
  [:bones/command
   ;; need to use namespace so this can be called from a macro (defroutes)
   ^:interceptors ['bones.http.handlers/error-responder ;; must come first
                   (cn/negotiate-content ["application/edn"])
                   'bones.http.handlers/session ;; auth
                   'bones.http.handlers/identity-interceptor ;; auth
                   'bones.http.auth/interceptor ;; auth
                   (body-params/body-params)
                   'bones.http.handlers/body-params
                   'bones.http.handlers/check-command-exists
                   'bones.http.handlers/check-args-spec
                   'bones.http.handlers/renderer
                   ]
   'bones.http.handlers/command-handler])

(defn command-list-resource [conf]
  [:bones/command-list (get-resource-interceptors) 'bones.http.handlers/command-list-handler])

(defn login-resource [conf]
  [:bones/login
   ^:interceptors ['bones.http.handlers/error-responder ;; must come first
                   (cn/negotiate-content ["application/edn"])
                   'bones.http.handlers/session ;; auth
                   (body-params/body-params)
                   'bones.http.handlers/body-params ;;shim
                   'bones.http.handlers/check-command-exists ;; :login should be registered
                   'bones.http.handlers/check-args-spec ;; user defined login parameters
                   'bones.http.handlers/renderer
                   ]
   'bones.http.handlers/login-handler])

(defrecord CQRS [conf]
  component/Lifecycle
  (start [cmp]
    (let [config (get-in cmp [:conf :http/handlers])]
      (assoc cmp :routes
             (io.pedestal.http.route/expand-routes
              [[[(or (:mount-path config) "/api")
                 ["/command"
                  {:post (command-resource config)
                   :get (command-list-resource config)}]
                 ;; ["/query"
                 ;;  {:get (query-resource config)}]
                 ["/login"
                  {:post (login-resource config)}]]]])))))
