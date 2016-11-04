(ns weather-report.macros)
;; clojurescript needs a separate namespace for macros

(defmacro button [label event & callbacks]
  `[:button {:on-click #(re-frame.core/dispatch ~event)}
    ~label])
