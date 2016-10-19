(ns weather-report.handlers-test
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [weather-report.handlers :as handlers]
            [weather-report.core :as core]
            [weather-report.subs] ;for :acccounts
            [cljs.test :refer-macros [deftest testing is async]]
            [cljs.core.async :as a]
            [re-frame.core :as r]
            [bones.client :as client]
            ))

(defn msg-event-source [{:keys [url onmessage onerror onopen]}]
  (go (a/<! (a/timeout 100))
      (onmessage (new js/MessageEvent
                      "message"
                      #js{:data (str {:a "message"})}))
      (onopen 'ok)
      {}))

(def sys (atom {}))

(doseq [h handlers/handlers]
  (handlers/register h)) ;; not lazy

(client/build-system sys {:url "url"
                          :req/post-fn (fn [url params]
                                         (go {:status 200
                                              :body "hello"}))
                          :es/constructor msg-event-source})

(client/start sys)

(core/subscribe-client (:client @sys))
(r/dispatch [:initialize-db sys])

(deftest submit-form-test
  (testing "add-account"
    (async done
           (let [account {:account/xact-id 123 :account/evo-id 321}
                 ;; _ (a/put! (get-in @sys [:client :pub-chan]) {:channel :response/command
                 ;;                                              :response {:status 200
                 ;;                                                         :body "hello"}})
                 revent-stream (r/subscribe [:accounts])]
             (r/dispatch [:response/command
                          {:status 200
                           :body account}
                          200
                          {}])
             (r/dispatch [:client/command
                          {:command :add-account
                           :args account}
                          {}])

             (is (= {} @re-frame.db/app-db))

             (is (= @revent-stream 123))
             (go (a/<! (a/timeout 10))
                 ;; (is (= @revent-stream 123))
                 (is (= 123 123))
                 (done))
       ))))
