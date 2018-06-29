(ns weather-report.login-test
  (:require [clojure.test :refer [deftest testing is]]
            [etaoin.api :as e]
            [etaoin.keys :as k]
            [clojure.spec :as s]
            [clojure.java.io :as io]))

(def conf (atom {:driver :phantom
                 ;; :site "http://localhost:3449/"
                 :site (str "file://" (.getPath (io/resource "public/index.html")))

                 }))


(defn login [d username password]
  (doto d
    (e/click :login)
    (e/fill-human :username username)
    (e/fill-human :password password)
    (e/click :submit)))

(defmacro then [text assertion]
  `(is ~assertion ~text))

(defmacro given [text letform & body]
  `(testing ~text
    (let ~letform
      ~@body)))

(defmacro pose [text & body]
  `(let [t# ~text]
     ~@body))

(defmacro note [& texts]
  `(let [t# (str ~@texts)]
     ))

(defn text [label]
  (str"//*[text()='" label "']"))

(deftest login-test
  (let [d (e/phantom)
        {:keys [site]} @conf
        ;; site "http://localhost:3449/"
        username "abcd"
        password "abcd"]
    (e/go d site)
    (login d username password)
    (then "the user is greeted with 'Hello'"
          (e/has-text? d (str "Hello " username)))))

(defn new-account-fusion [d xact-id evo-id]
  (pose "a user clicks 'New Account Fusion'"
          (e/click d :new-account-fusion))
  (pose "submits xact-id: 123 and evo-id: 321"
        (e/fill d :xact-id xact-id)
        (e/fill d :evo-id evo-id)
        (e/click d :submit)
        (e/wait-exists d :evo-id-123))
  (then "should see 123"
        (e/has-text? d evo-id)))

(defn update-value [d id value]
  (let [el-id (str "evo-id-" id)]
    (doto d
      (e/click (str ".//*[@id='" el-id "']"))
      (e/wait-exists :evo-id)) ;; form input appears
    ;; remove existing and then some
    (apply e/fill d :evo-id (repeat 6 k/backspace))
    (doto d
      ;; new value plus enter to save
      (e/fill :evo-id (str value "\n"))
      (e/wait-absent :evo-id)) ;; form element disappears
    ))

(deftest add-account
  (given "a user is logged in"
         [d (e/phantom)
          ;; site "http://localhost:3449/"
          {:keys [site]} @conf
          _  (e/go d site)
          _ (login d "abcd" "abcd")]

         (pose "create a new account"
               (new-account-fusion d "123" "321"))

         (then "should see new value '321'"
               (e/has-text? d "321"))

         (pose "upate the evo-id to 456"
               (update-value d "123" "456"))

         (note "hitting enter while entering a value will submit the form,"
               "though it is not documented, it is assumed that this is understood to be normal browser behavior")

         (then "should see new value '456'"
               (e/has-text? d "456"))))

(comment
  (def d (e/phantom))
  ;; (e/go d "http://localhost:3449/")
  (e/go d (:site @conf))

  (e/click d (text "Cancel"))

  (login d "arst" "arst")

  (new-account-fusion d "123" "321")

  (new-account-fusion "456" "654")


  ;; (e/query d (text "321"))

  (e/query d :evo-id-123)

  (e/query d ".//*[@id='evo-id-123']")


  (e/fill d :evo-id
          k/escape)


  (e/js-execute d "document.getElementById(arguments[0]).value = arguments[1];", "evo-id", "")

  (e/reload d )

  (e/screenshot d "screenshot.png")

  )
