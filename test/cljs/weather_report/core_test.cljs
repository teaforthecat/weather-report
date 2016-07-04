(ns weather-report.core-test
  (:require [cljs.test :refer-macros [deftest testing is]]
            [weather-report.core :as core]))

(deftest fake-test
  (testing "fake description"
    (is (= 1 1))))
