(ns weather-report.runner
    (:require [doo.runner :refer-macros [doo-tests]]
              [weather-report.components-test]
              ))

(doo-tests 'weather-report.components-test
           )
