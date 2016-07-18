(ns bones.http.handlers-test
  (:require [bones.http.handlers :as handlers]
            [byte-streams :as bs]
            [clojure
             [edn :as edn]
             [test :refer [deftest is testing]]]
            [io.pedestal.test :refer [response-for]]
            [io.pedestal.http :as bootstrap]
            [io.pedestal.http.route.definition :refer [defroutes]]
            [bones.http.service :as service]
            [ring.mock.request :as mock]
            [schema.core :as s]
            ))

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

(defroutes routes
  [[["/api"
     ["/command"
      {:post (handlers/command-resource {})}]
     ]]])

(def service
  (::bootstrap/service-fn (bootstrap/create-servlet (service/service routes {}))))

(defn edn-post [body-params]
  (response-for service
                :post "/api/command"
                :body (pr-str body-params)
                :headers {"Content-Type" "application/edn"
                          "Accept" "application/edn"}))

(deftest cqrs-test
  (let [body-params {:command :ping :args {:a 1}}
        response (edn-post body-params)]
    (is (= (:body response) "pong"))
    (is (= (get (:headers response) "Content-Type") "application/edn"))
    (is (= (:status response) 200))))
