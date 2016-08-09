(ns bones.http.core-test
  (:require [bones.http.core :as http]
            [clojure.test :refer [deftest testing is]]))

;; conf for -main
;; to block caller indefinitely add:
;; {:http/service {:join? true}}
(def sys (atom {}))
(def conf {:http/auth {:secret "1234"
                       :cookie-name "pizza"
                       :cookie-secret "a 16 byte string"}})

(deftest start-system
  (testing "shield gets created and used by routes"
    (http/build-system sys conf)
    (http/start-system sys)
    (is (= "1234" (apply str (map char  (get-in @sys [:routes :shield :secret])))))
    (is (= "pizza" (get-in @sys [:routes :shield :cookie-opts :cookie-name])))
    (http/stop-system sys)))
