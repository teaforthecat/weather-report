(ns weather-report.worker
  (:require [com.stuartsierra.component :as component]
            [bones.conf :as conf]
            [bones.stream.core :as stream]
            [weather-report.jobs :as jobs]))


;; create global state
(def system (atom {}))

(defn -main []
  (let [config (conf/map->Conf {:conf-files ["resources/dev-config.edn"
                                          "resources/stream.edn"]})

        job (jobs/xact-evo-data-share config)
        ;; used in web:
        ;; (stream/pipeline job)
        ]
    (stream/build-system system config)
    (stream/start system)
    (stream/submit-job system job)
    ) )

(comment
  (-main)
  (stream/kill-jobs system)
  (stream/stop system)

  )
