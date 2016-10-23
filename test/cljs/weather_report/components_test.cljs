(ns weather-report.components-test
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [weather-report.components :as c]
            [re-frame.core :refer [dispatch subscribe dispatch-sync]]
            [cljs.core.async :as a]
            [cljs.test :refer-macros [deftest testing is async]]))


(deftest login
  (testing "logout button shows only when logged in"
    (let [output (c/login)]
      (is (= "Login"
             (nth (output) 2)))
      (dispatch-sync [:event/client-status {:bones/logged-in? true}])
      (is (= "Logout"
             (nth (output) 2)))))
  (testing "login form is shown"
    (let [output (c/login-form) ]
      (is (= nil (output)))
      (dispatch-sync [:component/show :login-form])
      (is (= [:div.form
              [:h3 "Login Form"]] (output)))
      )))


(deftest accounts
  (testing "one account is rendered"
    (let [account {:account/xact-id 123 :account/evo-id 321}
          output (c/accounts-list)]
      (dispatch-sync [:event/message account])
      (is (= 123 (get-in (output)
                         [1 ; :div.accounts-list
                          1 ; :ul
                          1 ; :li
                          1 ; :span
                          ])))))
  (testing "adding an account"
    (let [output (c/add-account)]
      (is (= "Add Account" (nth (output) 2)))
      (dispatch-sync [:component/show :add-account])
      (is (= [:h3 "Add Account"] (nth (output) 1)))
      (dispatch-sync [:component/hide :add-account])
      (is (= "Add Account" (nth (output) 2)))
      )))
