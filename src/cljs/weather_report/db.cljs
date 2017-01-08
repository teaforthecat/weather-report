(ns weather-report.db
  (:require [weather-report.local-storage :as storage]
            [accounts :as accounts]))


(defn get-storage-item [^Keyword component]
  (if-let [user-info (storage/get-item component)]
    (cljs.reader/read-string user-info)))

(defn set-storage-item [^Keyword component value]
  (if (nil? value)
    (storage/remove-item! component)
    (storage/set-item! component (pr-str value))))

(def default-db
  {:name "re-frame"
   :accounts ()
   :editable {:accounts {:_meta {:outgoing-spec :accounts/fusion}}}
   ;; we want this to just happen once on page load
   :components {:user-info (get-storage-item :user-info)
                :undos ()}})
