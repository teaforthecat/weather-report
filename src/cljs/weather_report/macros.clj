(ns weather-report.macros)
;; clojurescript needs a separate namespace for macros

(defmacro button [label event & class]
  `[:button {:on-click #(re-frame.core/dispatch ~event)
             :class (clojure.string/join " " (conj ["sr-button"] ~@class))}
    ~label])
