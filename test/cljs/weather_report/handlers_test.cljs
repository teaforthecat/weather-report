(ns weather-report.handlers-test
  (:require [weather-report.handlers :as handlers]
            [cljs.test :refer-macros [deftest testing is]]
            ))

(deftest submit-form-test
  (testing "add-account"
    (let [account {:account/xact-id 123 :account/evo-id 321}
          result (handlers/submit-form :add-account {:accounts []} (atom account) {})]
      (is (= 123 (get-in result [:accounts 0 :account/xact-id])))))
  (testing "login"
    (let [data {:username "bob" :password "abc123"}
          result (handlers/submit-form :login {} (atom {}) {})]
      (is (= true (:bones/logged-in? result))))))
