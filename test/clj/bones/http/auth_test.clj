(ns bones.http.auth-test
  (:require [bones.http.auth :as auth]
            [ring.middleware.session :refer [wrap-session session-request session-response]]
            [ring.middleware.session.store :as store]
            [ring.util.codec :as codec]
            [clojure.test :refer [deftest testing is]]))

(defn session [sheild data]
  (let [{:keys [cookie-opts cookie-secret]} sheild]
    (store/write-session (:store cookie-opts) cookie-secret data)))

(defn read-session [sheild sess]
  (let [{:keys [cookie-opts]} sheild]
    (store/read-session (:store cookie-opts) sess)))

(def session-info {:identity {:xyz 123}})

(deftest request-session
  (let [sheild (.start (auth/map->Sheild {}))]
    (testing "given an empty request and response, sets identity to nil"
      (let [req ((auth/identity-interceptor sheild) {} )]
        (is (= {:identity nil} req))))
    (testing "session is readable and writable"
      (is (= (read-session sheild (session sheild session-info)) session-info)))))


(deftest test-sheild
  (testing "workable defaults"
    (let [sheild (.start (auth/map->Sheild {}))]
      (testing "extacts data from token to identity"
        ;; all token data ends up in :identity
        (let [valid-request {:headers {"authorization" (str "Token " (.token sheild {:xyz 123}))}}
              req ((auth/identity-interceptor sheild) valid-request)]
          (is (= 123 (get-in req [:identity :xyz])))))
      (testing "extracts data from cookie to identity"
        ;; session data must have data within :identity
        (let [value (session sheild session-info)
              valid-request {:headers {"cookie" (codec/form-encode {"bones-session" value})}}
              req ((auth/identity-interceptor sheild)
                   (session-request valid-request (:cookie-opts sheild)))]
          (is (= 123 (get-in req [:identity :xyz]))))))))
