(ns weather-report.accounts
  (:require #?(:cljs [cljs.spec :as s]
               :clj [clojure.spec.alpha :as s])
            ))

(defn x-integer? [x]
  (if (integer? x)
    x
    (if (string? x)
      (let [n #?(:cljs (js/parseInt x)
                 :clj (Integer. (re-find  #"\d+" x)))]
        (if (integer? n)
          n
          :cljs.spec/invalid))
      :cljs.spec/invalid)))

(def int! (s/with-gen (s/conformer x-integer?)
            #(s/gen int?)))

(s/def ::xact-id int!)

(s/def ::evo-id (s/nilable int!))

(s/def ::fusion (s/keys :req-un [::xact-id ::evo-id]))

(s/def ::upsert (s/keys :req-un [::xact-id ::evo-id]))

(s/def ::delete (s/and (s/keys :req-un [::xact-id ::evo-id]) (comp nil? :evo-id)))

(s/def ::account int!)

;; this is weird - maybe coerce?
(s/def ::accounts (s/or :ids (s/* int!) :all #(= ":all" %)))

;; bad model here: use xact-id:
(s/def ::list (s/or :accounts (s/keys :req-un [::accounts])
                    :account (s/keys :req-un [::account]) ))

(comment

  (s/valid? ::list {:accounts ":all"})
  (s/valid? ::delete {:xact-id 123 :evo-id nil})

  (defn upsert [args auth-info req]
    args)

  (s/fdef upsert
          :args (s/cat :args ::upsert :auth-info map? :req map?)
          :ret map?)


  (require '[clojure.spec.alpha.gen :as gen])

  (gen/generate  (s/gen ::evo-id))

  (s/exercise-fn `upsert)

  )
