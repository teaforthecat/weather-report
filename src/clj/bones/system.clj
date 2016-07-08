(ns bones.system
  (:require [com.stuartsierra.component :as component]))

(def sys (atom {}))

(defn add-component
  "handles the dependency declaration of conf, registered in the dependency graph"
  [name component-record & dependencies]
  (let [d (into [:conf] dependencies)
        ;; conf depends on none, esp. not self
        deps (if (= :conf name) [] d)]
    ;; first add a conf on which to depend
    (if (and (nil? (:conf @sys)) (not= :conf name))
      (do
        (println "warning no configuration found")
        (swap! sys assoc :conf {})))
    ;; then add the component
    (swap! sys assoc name (component/using component-record deps))))

(defn start-system
  "handles the dependency injection of conf
  conf is always started first to read conf data
  calls start on all the components passed in, in order
  > (start-system :a :b :c)"
  [ & component-names]
  (let [comps (into [:conf] component-names)]
    (swap! sys component/update-system comps component/start)))

(defn stop-system
  "calls stop on all the components passed in, in reverse order
  > (stop-system :a :b :c)"
  [ & components]
  (swap! sys component/update-system-reverse components component/stop))
