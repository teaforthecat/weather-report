(ns weather-report.user
  (:require #?(:cljs [cljs.spec :as s]
               :clj [clojure.spec :as s])
            ))

(s/def ::username string?)
(s/def ::password string?)
(s/def ::login (s/keys :req-un [::username ::password]))
