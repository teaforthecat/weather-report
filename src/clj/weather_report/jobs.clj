(ns weather-report.jobs
  (:require [bones.stream.jobs :as jobs]))

(defn xact-evo-data-share [config]
  (jobs/series-> (get-in config [:stream])
                 (jobs/input :kafka {:kafka/topic "xact-evo-data-share"
                                     :serialization-format :json-plain
                                     ;; :kafka/serializer-fn :bones.stream.serializer/en-json-plain
                                     ;; :kafka/deserializer-fn :bones.stream.serializer/de-json-plain
                                     })
                 (jobs/output :redis {:redis/channel "xact-evo-data-share"})))

(comment
  (def job (xact-evo-data-share {}))
  (meta (first (:catalog job))) )
