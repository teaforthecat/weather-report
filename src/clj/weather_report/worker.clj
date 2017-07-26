(ns weather-report.worker
  (:require [com.stuartsierra.component :as component]
            [bones.conf :as conf]
            [bones.stream.jobs :as jobs]
            [bones.stream.core :as stream]))


;; create global state
(def system (atom {}))

(defn -main []
  (let [cfg (conf/map->Conf {:conf-files ["resources/dev-config.edn"
                                          "resources/stream.edn"]})
        job (jobs/series-> (get-in cfg [:stream])
                           (jobs/input :kafka {:kafka/topic "xact-evo-data-share"
                                               :kafka/serializer-fn :bones.stream.serializer/en-json-plain
                                               :kafka/deserializer-fn :bones.stream.serializer/de-json-plain})
                           (jobs/output :redis {:redis/channel "xact-evo-data-share"}))
        ;; use in web:
        ;; pipeline (stream/pipeline job)
        ]
    (stream/build-system system cfg)
    (stream/start system)
    (stream/submit-job system job)
    ) )

(comment
  (-main)
  (stream/kill-jobs system)
  (stream/stop system)

  )
