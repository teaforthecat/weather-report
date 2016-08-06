(ns bones.http.auth
  (:require [buddy.sign.jwe :as jwe]
            [buddy.core.nonce :as nonce]
            [buddy.auth :refer [authenticated? throw-unauthorized]]
            [buddy.auth.backends.token :refer [jwe-backend]]
            [buddy.auth.backends.session :refer [session-backend]]
            [buddy.auth.middleware :refer [wrap-authentication wrap-authorization]]
            [buddy.hashers :as hashers]
            [clj-time.core :as time]
            [ring.middleware.session.cookie :refer [cookie-store]]
            [ring.middleware.session :refer [wrap-session session-request session-response]]
            [ring.middleware.session.store :as store]))

;; TODO: make all this configurable
(def secret (nonce/random-bytes 32))
(def algorithm {:alg :a256kw :enc :a128gcm})
(def auth-backend (jwe-backend {:secret secret :options algorithm}))
(def cookie-session-backend (session-backend))
(def cookie-secret "a 16-byte secret")
(def cookie-opts
  {:store (cookie-store {:key cookie-secret})
   ;; TODO: add :secure true
   :cookie-name "bones-session"
   :cookie-attrs {:http-only false
                  ;; one year
                  :max-age (* 60 60 24 365)}})

(defn encrypt-password [password]
  (hashers/encrypt password {:alg :bcrypt+blake2b-512}))

(defn check-password [password-candidate encrypted-password]
  (hashers/check password-candidate encrypted-password))

(defn token [data]
  (let [exp (time/plus (time/now) (time/hours 1)) ;; todo make configurable
        claims (assoc data :exp exp)]
    (jwe/encrypt claims secret algorithm)))

(def interceptor
  {:name :bones.auth/authentication
   :enter (fn [ctx]
            ;; maybe check for identity directly here, to make it obvious
            (if (authenticated? (:request ctx))
              ctx
              (throw (ex-info "Not Authenticated" {:status 401}))))})

(defn identity-interceptor []
  ;; identity above refers to buddy's idea of authentication identity
  ;; identity below is clojure.core; to hack the ring middleware for pedestal
  (wrap-authentication identity auth-backend cookie-session-backend))
