(defproject weather-report "0.1.0-SNAPSHOT"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.8.51"]
                 [reagent "0.5.1"]
                 [binaryage/devtools "0.6.1"]
                 [re-frame "0.7.0"]
                 [secretary "1.2.3"]
                 ;; bones deps
                 [io.pedestal/pedestal.service "0.5.0"]
                 [io.pedestal/pedestal.jetty "0.5.0"]
                 [manifold "0.1.4"]
                 [buddy/buddy-auth "0.8.1"]
                 [buddy/buddy-hashers "0.9.1"]
                 [prismatic/schema "1.1.2"]
                 [com.taoensso/carmine "2.12.2"]

                 ;; pedestal deps - necessary?
                 [ch.qos.logback/logback-classic "1.1.7" :exclusions [org.slf4j/slf4j-api]]
                 [org.slf4j/jul-to-slf4j "1.7.21"]
                 [org.slf4j/jcl-over-slf4j "1.7.21"]
                 [org.slf4j/log4j-over-slf4j "1.7.21"]

                 ;; bones cljs
                 [reagent-forms "0.5.24"]
                 [cljs-http "0.1.39"]
                 ]

  :plugins [[lein-cljsbuild "1.1.3"]]

  :min-lein-version "2.5.3"

  :source-paths ["src/clj"]
  :test-paths   ["test/clj"]

  :clean-targets ^{:protect false} ["resources/public/js/compiled" "target"
                                    "test/js"]

  :figwheel {:css-dirs ["resources/public/css"]}

  :repl-options {:nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}

  :main ^{:skip-aot true} weather-report.core

  :profiles
  {:dev
   {:dependencies [
                   [figwheel-sidecar "0.5.4-3"]
                   [com.cemerick/piggieback "0.2.1"]
                   [io.pedestal/pedestal.service-tools "0.5.0"]]

    :plugins      [[lein-figwheel "0.5.4-3"]
                   [lein-doo "0.1.6"]
                   ]
    :source-paths ["dev"]
    }
   :uberjar {:aot [weather-report.server]}}

  :cljsbuild
  {:builds
   [{:id           "dev"
     :source-paths ["src/cljs"]
     :figwheel     {:on-jsload "weather-report.core/mount-root"}
     :compiler     {:main                 weather-report.core
                    :output-to            "resources/public/js/compiled/app.js"
                    :output-dir           "resources/public/js/compiled/out"
                    :asset-path           "js/compiled/out"
                    :source-map-timestamp true}}

    {:id           "min"
     :source-paths ["src/cljs"]
     :compiler     {:main            weather-report.core
                    :output-to       "resources/public/js/compiled/app.js"
                    :optimizations   :advanced
                    :closure-defines {goog.DEBUG false}
                    :pretty-print    false}}
    {:id           "test"
     :source-paths ["src/cljs" "test/cljs"]
     :compiler     {:output-to     "resources/public/js/compiled/test.js"
                    :main          weather-report.runner
                    :optimizations :none}}
    ]}

  )
