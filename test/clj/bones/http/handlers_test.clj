(ns bones.http.handlers-test
  (:require [bones.http.handlers :as handlers]
            [byte-streams :as bs]
            [clojure.edn :as edn]
            ;; [clojure.test :refer [deftest testing is thrown?]]
            [clojure.test :refer :all]
            [io.pedestal.test :refer [response-for]]
            [io.pedestal.http :as bootstrap]
            [io.pedestal.http.route.definition :refer [defroutes]]
            [bones.http.service :as service]
            [ring.mock.request :as mock]
            [schema.core :as s]
            ))

(defn hello [args]
  (let [who (get-in args [:who])]
    {:message (str "hello " who)}))

(handlers/register-command :hello {:who s/Str})

(defn edn-request [body]
  (-> (mock/request :post "/" (pr-str body))
      (mock/content-type "application/edn")))

(defn edn-body [{:keys [body]}]
  (-> body bs/to-string edn/read-string))

(def routes (->  (handlers/map->CQRS {}) .start :routes))

(def service
  (::bootstrap/service-fn (bootstrap/create-servlet (service/service routes {}))))

(defn edn-post [body-params]
  (response-for service
                :post "/api/command"
                :body (pr-str body-params)
                :headers {"Content-Type" "application/edn"
                          "Accept" "application/edn"}))

(defn edn-get [path]
  (response-for service
                :get path
                :headers {"Content-Type" "application/edn"
                          "Accept" "application/edn"}))


(deftest register-command-test
  (testing "optional handler argument"
    (is (handlers/register-command :test {} ::hello)))
  (testing "explicit handler with a namespace"
    (is (handlers/register-command :test {} ::handlers/echo)))
  (testing "non existing function throws error"
    (is (thrown? clojure.lang.ExceptionInfo
                 (handlers/register-command :nope {})))))

(deftest command-resource-test
  (testing "non-existant args"
    (let [response (edn-post {:command :echo #_no-args})]
      (is (= (:body response) "{:args missing-required-key}"))
      (is (= (get (:headers response) "Content-Type") "application/edn"))
      (is (= (:status response) 401))))

  (testing "non-existant command"
    (let [response (edn-post {:command :nuthin})]
      (is (= (:body response) "{:message \"command not found: :nuthin\", :available-commands (:echo :hello)}"))
      (is (= (get (:headers response) "Content-Type") "application/edn"))
      (is (= (:status response) 401))))

  (testing "args that are something other than a map"
    (let [response (edn-post {:command :echo :args [:not :a-map]})]
      (is (= (:body response) "{:args (not (map? [:not :a-map]))}"))
      (is (= (get (:headers response) "Content-Type") "application/edn"))
      (is (= (:status response) 401))))

  (testing "built-in echo command with valid args"
    (let [response (edn-post {:command :echo :args {:yes :allowed}})]
      (is (= (:body response) "{:yes :allowed}"))
      (is (= (get (:headers response) "Content-Type") "application/edn"))
      (is (= (:status response) 200))))

  (testing "registered command with valid args"
    (let [body-params {:command :hello :args {:who "mr teapot"}}
          response (edn-post body-params)]
      (is (= (:body response) "{:message \"hello mr teapot\"}"))
      (is (= (get (:headers response) "Content-Type") "application/edn"))
      (is (= (:status response) 200)))))

(deftest command-list-resource-test
  (testing "get"
    (let [response (edn-get "/api/command")]
      (is (= (:body response) "({:args {Any Any}, :command (enum :echo)} {:args {:who java.lang.String}, :command (enum :hello)})"))
      (is (= (get (:headers response) "Content-Type") "application/edn"))
      (is (= (:status response) 200)))))
