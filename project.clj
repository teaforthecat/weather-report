(defproject weather-report "0.2.0"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.9.229"]
                 [reagent "0.5.1"]
                 [binaryage/devtools "0.6.1"]
                 [re-frame "0.7.0"]
                 [secretary "1.2.3"]
                 [com.stuartsierra/component "0.3.1"]
                 ;; bones deps
                 [bones/conf "0.2.2"]
                 [bones/http "0.2.2"]
                 ;; bones.stream
                 [com.taoensso/carmine "2.12.2"]
                 [org.onyxplatform/onyx-kafka "0.8.8.0"]
                 [manifold "0.1.4"]
                 ;; bones cljs
                 [reagent-forms "0.5.24"]
                 [bones/client "0.2.2"]
                 ]

  :plugins [[lein-cljsbuild "1.1.3"]]

  :min-lein-version "2.5.3"

  :resource-paths ["resources"]
  :source-paths ["src/clj" "src/cljs"] ;; src/cljs for macros.clj
  :test-paths   ["test/clj"]

  :clean-targets ^{:protect false} ["resources/public/js/compiled" "target"
                                    "test/js"]

  :figwheel {:css-dirs ["resources/public/css"]}

  :repl-options {:nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}

  ;; todo: what does aot do?
  ;; :main ^{:skip-aot true} weather-report.core
  :main weather-report.core

  :profiles
  {:dev
   {:dependencies [
                   [figwheel-sidecar "0.5.4-3"]
                   [com.cemerick/piggieback "0.2.1"]]
    :plugins      [[lein-figwheel "0.5.4-3"]
                   [lein-doo "0.1.7"]
                   ]
    :source-paths ["dev"]
    }
   :uberjar {:aot [weather-report.core]}}

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
