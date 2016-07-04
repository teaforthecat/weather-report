(ns user
  (:require [figwheel-sidecar.repl-api :as ra]))


(defn start []
  (ra/start-figwheel!))

(defn cljs []
  (ra/cljs-repl "dev"))
