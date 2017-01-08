(ns accounts
  (:require #+cljs[cljs.spec :as s]
            #+clj[clojure.spec :as s]
            ))



(s/def ::xact-id integer?)
(s/def ::evo-id (s/nilable integer?))

(s/def ^{:command} ::upsert (s/keys :req-un [::xact-id ::evo-id]))

(s/def ::fusion (s/keys :req-un [::xact-id ::evo-id]))
