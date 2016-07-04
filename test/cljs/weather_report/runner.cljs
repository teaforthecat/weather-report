(ns weather-report.runner
    (:require [doo.runner :refer-macros [doo-tests]]
              [weather-report.core-test]
              [weather-report.handlers-test]))

(doo-tests 'weather-report.core-test
           'weather-report.handlers-test)
