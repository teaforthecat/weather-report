(ns bones.http.handlers-test
  (:require [bones.http.handlers :as handlers]
            [byte-streams :as bs]
            [clojure
             [edn :as edn]
             [test :refer [deftest is testing]]]
            [ring.mock.request :as mock]
            [schema.core :as s]
            [yada
             [handler :refer [handler]]
             [test :refer [response-for]]]))

(defmethod handlers/command :hello
  [command]
  (let [who (get-in command [:args :who])]
    {:message (str "hello " who)}))

(handlers/add-command [:hello {:who s/Str}])


(comment
  ;; maybe this could work
  (handlers/register-command :hello {:who s/Str}
                             (fn [args]
                               (print-str args)
                               (let [who (get-in args [:who])]
                                 {:message (str "hello " who)})))

  )

(def some-jobs
  {:bones.core/wat {:weight-kg s/Num
                    :name s/Str}
   :bones.core/who {:name s/Str
                    :role s/Str}})

(defn edn-request [body]
  (-> (mock/request :post "/" (pr-str body))
      (mock/content-type "application/edn")))

(defn edn-body
  [{:keys [body]}]
  ;; not sure why the inconsistency here, perhaps the custom 400 response?
  (if (instance? schema.utils.ValidationError body)
    body
    (-> body bs/to-string edn/read-string))
  )

(deftest cqrs
  (let [resource (handlers/command-resource {})
        h (handler resource)]
    (testing "invalid command value receives 400"
      (let [response @(h (edn-request {:command :x}))]
        (is (= 400 (:status response)))
        (let [body (edn-body response)]
          (is (= "(not (#{:bones.core/wat :hello :bones.core/who} (:command {:command :x})))"
                 (pr-str body)))))
      )
    (testing "invalid args will receive 400 and an error message"
      (let [response @(h (edn-request {:command :hello :args {:what "wrong key"}}))
            ]
        (is (= 400 (:status response)))
        (let [body (edn-body response)]
          (is (= '{:args {:who missing-required-key,
                          :what disallowed-key}} body)))))
    (testing "command uses multimethod"
        (let [response @(h (edn-request {:command :hello :args {:who "world"}}))]
          (is (= 200 (:status response)))
          (is (= {:message "hello world"} (edn-body response)))))
    )

  )
