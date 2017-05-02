(ns weather-report.subs
  (:require-macros [reagent.ratom :refer [reaction]])
  (:require [re-frame.core :as re-frame]
            [weather-report.local-storage :as storage]))


(re-frame/reg-sub
 ;; non-editable things
 :components
 (fn [db args]
   (get-in db args)))

