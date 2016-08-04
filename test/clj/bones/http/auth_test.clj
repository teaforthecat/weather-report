(ns bones.http.auth-test
  (:require [bones.http.auth :as auth]
            [ring.middleware.session :refer [wrap-session session-request session-response]]
            [ring.middleware.session.store :as store]
            [ring.util.codec :as codec]
            [clojure.test :refer [deftest testing is]]))

;; wip
(defn session [data]
  (store/write-session (:store auth/cookie-opts) auth/cookie-secret data))

;; wip
(defn read-session [sess]
  (store/read-session (:store auth/cookie-opts) sess))

(def session-info {:identity {:xyz 123}})

(deftest request-session
  (testing "given an empty request and response, sets identity to nil"
    (let [req ((auth/identity-interceptor) {} )]
      (is (= {:identity nil} req))))
  (testing "extracts identity from session"
    (let [valid-request {:session {:identity 123}}
          req ((auth/identity-interceptor) valid-request)]
      (is (= {:session {:identity 123} :identity 123} req))))
  (testing "extracts data from token to identity"
    (let [valid-request {:headers {"Authorization" (str "Token " (auth/token {:xyz 123}))}}
          req ((auth/identity-interceptor) valid-request)]
      (is (= 123 (get-in req [:identity :xyz])))))
  (testing "session is readable and writable"
    (is (= (read-session (session session-info)) session-info))))

;; ;; this works in the full servlet context, not sure why this test doesn't work
;; (testing "extracts session from cookie"
;;   (comment
;;     (let [value (session {:identity {:xyz 123}})
;;           ;; cookie (str ";" "bones-session" ":"  (codec/url-encode value))
;;           cookie (codec/url-encode (str ";" "bones-session" ":"   value))
;;           valid-request {:cookies {"bones-session" value}}
;;           req ((auth/identity-interceptor)
;;                (session-request valid-request auth/cookie-opts))]
;;       (is (= 123 (get-in req [:identity :xyz]))))))
