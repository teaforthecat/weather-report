(ns weather-report.config)

(def debug?
  ^boolean js/goog.DEBUG)

(when debug?
  ;; is this redundant?
  ;; (enable-console-print!)

  )
