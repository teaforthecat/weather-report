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

;; add secret to conf
(def secret (nonce/random-bytes 32))
(def algorithm {:alg :a256kw :enc :a128gcm})
(def auth-backend (jwe-backend {:secret secret :options algorithm}))
(def cookie-session-backend (session-backend))
(def cookie-secret "a 16-byte secret")
(def cookie-opts
  {:store (cookie-store {:key cookie-secret})
   ;; TODO: add :secure true
   :cookie-name "bones-session"
   :cookie-attrs {:http-only false}})

(defn encrypt-password [password]
  (hashers/encrypt password {:alg :bcrypt+blake2b-512}))

(defn check-password [password-candidate encrypted-password]
  (hashers/check password-candidate encrypted-password))

(defn token [data]
  (let [exp (time/plus (time/now) (time/hours 1)) ;; todo make configurable
        claims (assoc data :exp exp)]
    (jwe/encrypt claims secret algorithm)))

(defn session [data]
  (store/write-session (:store cookie-opts) cookie-secret data))

(defn read-session [sess]
  (store/read-session (:store cookie-opts) sess))

(comment
  ;; when copying from the repl output of printing the request, the "=" gets
  ;; http encoded(?) to %3d
  (read-session "vRu8o3avetgkDgiLqyCJ6T75m6V8fjt6VnRP3klThvz7ackbHcXLoaMX6izXeT4F--JtGidgQhNx3CmWYeNxWgl1t2mGWjW56pg8KVnAfZ2F8=")
  (read-session
   (session {:xyz 123})) ;;=> {:xyz 123}
  )

;; this must come first
(defn cookie-session-middleware []
  " a 16-byte secret key so sessions last across restarts"
  (wrap-session identity {:store (cookie-store {:key "a 16-byte secret"})
                         ;; TODO: add :secure true
                         :cookie-name "bones-session"
                         :cookie-attrs {:http-only false}}))


;; (defn login-handler [username password]
;;   (let [user-data (check-password username password)]
;;     (if user-data
;;       (let [claims {:user user-data
;;                     :exp (time/plus (time/now) (time/hours 1))}
;;             token (jwe/encrypt claims secret algorithm)]
;;         ;;setup both token and cookie
;;         ;;for /api/events EventSource
;;         (-> (ok {:token token})
;;             (update :session merge {:identity {:user user-data}})))
;;       (bad-request {:message "username or password is invalid"}))))

;; (cookie-session-middleware
;;  (wrap-authentication
;;   (eval (cqrs path jobs query-handler))
;;   auth-backend
;;   cookie-session-backend)
;;  )

(def interceptor
  {:name :bones.auth/authentication
   :enter (fn [ctx]
            (if (authenticated? (:request ctx))
              ctx
              (throw (ex-info "Not Authenticated" {:status 403 :message "Not Authenticated"}))))})

(defn identity-interceptor []
  ;; identity as function to hack the ring middleware for pedestal
  ;; identity also means authentication identity
  (wrap-authentication identity auth-backend cookie-session-backend))
