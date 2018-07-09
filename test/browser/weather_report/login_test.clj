(ns weather-report.login-test
  (:require [clojure.test :refer [deftest testing is]]
            [etaoin.api :as e]
            [etaoin.keys :as k]
            [clojure.spec.alpha :as s]
            [clojure.java.io :as io]
            [manifold.deferred :as d]))



(def conf (atom {:driver :phantom
                 :site "http://localhost:8080/"}))

(def level-1 "# %s \n")
(def level-2 "## %s \n")
(def level-3 "%s \n\n")
(def level-4 "%s \n\n")

(defmethod clojure.test/report :pass [m]
  (let [rep (clojure.test/inc-report-counter :pass)]
    (clojure.test/with-test-out
      (printf level-3 (str (:message m) " (ok)")))))

(defmethod clojure.test/report :begin-test-var [m]
  (clojure.test/with-test-out
    (printf level-2 (-> m :var meta :name))))


(defn login [d username password]
  (doto d
    (e/click :login)
    (e/fill-human :username username)
    (e/fill-human :password password)
    (e/click :submit)))

(defmacro then [text assertion]
  `(is ~assertion (str "then " ~text)))

(defmacro given [text letform & body]
  `(testing ~text
     (clojure.test/with-test-out
       (printf level-3 (str "Given " ~text)))
     ;; need to print before body for proper nesting
    (let ~letform
      ~@body)))

(defmacro pose [text & body]
  `(let [t# ~text
         result# (do ~@body)]
     ;; might be best to print after execution to indicate success
     (clojure.test/with-test-out
       (printf level-3 (str ~text)))))

(defmacro note [& texts]
  `(let [t# (str ~@texts)]
     (clojure.test/with-test-out
       (printf level-4 t#))))

(defn text [label]
  (str"//*[text()='" label "']"))

(deftest login-test
  (let [d (e/phantom)
        {:keys [site]} @conf
        username "abcd"
        password "abcd"]
    (testing "simple login"
      (e/go d site)
      (given "a user logs in with a valid username and password"
             [_ (login d username password)]
             (then "the user is greeted with 'Hello'"
                   (e/has-text? d (str "Hello " username)))))))

(defn new-account-fusion [d xact-id evo-id]
  (pose "and they click 'New Account Fusion'"
          (e/click d :new-account-fusion))
  (pose "and they submit xact-id: 123 and evo-id: 321"
        (e/fill d :xact-id xact-id)
        (e/fill d :evo-id evo-id)
        (e/click d :submit)
        (e/wait-exists d :evo-id-123))
  (then "they should see 123"
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
          {:keys [site]} @conf
          _  (e/go d site)
          _ (login d "abcd" "abcd")]

         (pose "when they create a new account fusion"
               (new-account-fusion d "123" "321"))

         (then "they should see new value '321'"
               (e/has-text? d "321"))

         (pose "and when they update the evo-id to 456"
               (update-value d "123" "456"))

         (note "hitting enter while entering a value will submit the form,"
               "though it is not documented, it is assumed that this is understood to be normal browser behavior")

         (then "they should see new value '456'"
               (e/has-text? d "456"))))

(comment
  (def d (e/phantom))
  ;; (e/go d "http://localhost:3449/")
  (e/go d (:site @conf))

  (e/click d (text "Cancel"))

  (login d "arst" "arst")

  (new-account-fusion d "123" "321")

  (new-account-fusion d "456" "654")

  ;; (e/query d (text "321"))

  (e/query d :evo-id-123)

  (e/query d ".//*[@id='evo-id-123']")


  (e/fill d :evo-id
          k/escape)


  (take 1
   (reverse
    (e/get-logs d)))

  (e/js-execute d "document.getElementById(arguments[0]).value = arguments[1];", "evo-id", "")

  (e/reload d )

  (e/screenshot d "screenshot.png")

  )
