(ns weather-report.runner
    (:require [doo.runner :refer-macros [doo-tests]]
              [weather-report.core-test]))

(doo-tests 'weather-report.core-test)
