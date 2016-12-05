(ns weather-report.db
  (:require [weather-report.local-storage :as storage]
            [cljs.spec :as s]))


(defn get-storage-item [^Keyword component]
  (if-let [user-info (storage/get-item component)]
    (cljs.reader/read-string user-info)))

(defn set-storage-item [^Keyword component value]
  (if (nil? value)
    (storage/remove-item! component)
    (storage/set-item! component (pr-str value))))

(s/def ::xact-id integer?)
(s/def ::evo-id (s/nilable integer?))
(s/def ::account (s/keys :req [::evo-id
                               ::xact-id]))

(def default-db
  {:name "re-frame"
   :accounts ()
   :cities ()
   ;; we want this to just happen once on page load
   :components {:user-info (get-storage-item :user-info)
                :forms {::account {}}
                :undos ()}})

(comment
  (get-in
   @re-frame.db/app-db
   [:components :add-account])

  )
