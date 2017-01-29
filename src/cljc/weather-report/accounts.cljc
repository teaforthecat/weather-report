(ns weather-report.accounts
  (:require #?(:cljs [cljs.spec :as s]
               :clj [clojure.spec :as s])
            ))

(defn x-integer? [x]
  (if (integer? x)
    x
    (if (string? x)
      (let [n (js/parseInt x)]
        (if (integer? n)
          n
          :cljs.spec/invalid))
      :cljs.spec/invalid)))

(s/def ::xact-id (s/conformer x-integer?))
(s/def ::evo-id (s/nilable (s/conformer x-integer?)))

(s/def ::fusion (s/keys :req-un [::xact-id ::evo-id]))

(s/def ::upsert (s/keys :req-un [::xact-id ::evo-id]))
