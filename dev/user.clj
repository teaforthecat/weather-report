(ns user
  (:require [figwheel-sidecar.repl-api :as ra]))


(defn fig []
  (ra/start-figwheel!))

(defn stop-fig []
  (ra/stop-figwheel!))

(defn cljs []
  ;; enter :cljs/quit to switch back
  (ra/cljs-repl "dev"))
