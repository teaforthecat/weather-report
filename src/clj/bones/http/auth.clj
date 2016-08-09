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
            [ring.middleware.session.store :as store]
            [com.stuartsierra.component :as component]))

(defn encrypt-password [password]
  (hashers/encrypt password {:alg :bcrypt+blake2b-512}))

(defn check-password [password-candidate encrypted-password]
  (hashers/check password-candidate encrypted-password))

(def check-authenticated
  {:name :bones.auth/check-authenticated
   :enter (fn [ctx]
            ;; maybe check for identity directly here, to make it obvious
            (if (authenticated? (:request ctx))
              ctx
              (throw (ex-info "Not Authenticated" {:status 401}))))})

(defn identity-interceptor [shield]
  ;; identity above refers to buddy's idea of authentication identity
  ;; identity below is clojure.core; to hack the ring middleware for pedestal
  (let [{:keys [token-backend cookie-backend]} shield]
    ;; (fn [])
    (wrap-authentication identity token-backend cookie-backend)))

(defn validate-cookie-secret [cookie-secret]
  (assert (= 16 (count cookie-secret))
          (str  "ring.middleware.session.cookie says the secret key must be exactly 16 bytes(characters); " (format "%s is %d" (pr-str cookie-secret) (count cookie-secret))) ))

(defprotocol Token
  (token [this data]))

(defrecord Shield [conf]
  Token
  (token [cmp data]
    (let [exp? (:token-exp-ever cmp)
          hours (:token-exp-hours cmp)
          secret (:secret cmp)
          algorithm (:algorithm cmp)
          exp (if exp?
                {:exp (time/plus (time/now) (time/hours hours))}
                {})
          claims (merge data exp)]
      (jwe/encrypt claims secret algorithm)))
  component/Lifecycle
  (start [cmp]
    (let [config (get-in cmp [:conf :http/auth])
          {:keys [secret
                  algorithm
                  cookie-secret
                  cookie-name
                  cookie-https-only
                  cookie-max-age
                  token-exp-hours
                  token-exp-ever?]
           :or {secret (nonce/random-bytes 32)
                algorithm {:alg :a256kw :enc :a128gcm}
                cookie-secret (nonce/random-bytes 16) ;; "a 16-byte secret"
                cookie-name "bones-session"
                cookie-https-only false
                cookie-max-age (* 60 60 24 365) ;; one year
                token-exp-hours (* 24 365) ;; one year
                token-exp-ever? false
                }} config]
      (validate-cookie-secret cookie-secret)
      (-> cmp
          (assoc :secret secret)
          (assoc :token-backend (jwe-backend {:secret secret :options algorithm}))
          (assoc :token-exp-ever? token-exp-ever?)
          (assoc :token-exp-hours token-exp-hours)
          (assoc :algorithm algorithm)
          (assoc :cookie-backend (session-backend))
          (assoc :cookie-opts {:store (cookie-store {:key cookie-secret})
                               :cookie-name cookie-name
                               :cookie-attrs {:http-only false
                                              :secure cookie-https-only
                                              :max-age cookie-max-age}})))))
