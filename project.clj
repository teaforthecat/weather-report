(defproject weather-report "0.2.3"
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/clojurescript "1.9.229"]

                 [com.stuartsierra/component "0.3.1"]
                 ;; bones deps
                 [bones/conf "0.2.2"]
                 [bones/http "0.3.4"]


                 ;; bones.stream
                 [com.taoensso/carmine "2.16.0"]
                 [org.onyxplatform/onyx-kafka "0.8.8.0"]
                 ;; [manifold "0.1.6-alpha4"] ;; provided by aleph
                 [com.cognitect/transit-clj "0.8.297"]

                 ;; domain specific
                 [org.clojars.pntblnk/clj-ldap "0.0.12"]

                 ;; bones cljs
                 ;; [reagent "0.6.0"]
                 ;; maybe?
                 [reagent "0.6.0" :exclusions [cljsjs/react]]
                 [cljsjs/react-with-addons "15.2.1-0"]
                 [re-frame "0.9.0"]
                 [secretary "1.2.3"]
                 [bones/client "0.2.6"]
                 [bones/editable "0.1.4"]

                 ;; browser
                 [etaoin "0.2.8-SNAPSHOT"]]

  :plugins [[lein-cljsbuild "1.1.5"]]

  :min-lein-version "2.5.3"

  :resource-paths ["resources"]
  :source-paths ["src/clj" "src/cljs" "src/cljc"]
  :test-paths   ["test/clj"]

  :clean-targets ^{:protect false} ["resources/public/js/compiled" "target"
                                    "test/js"]

  :figwheel {:css-dirs ["resources/public/css"]}

  :repl-options {:nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}

  :main weather-report.core

  :profiles
  {:dev
   {:dependencies [
                   [re-frisk "0.4.5"]
                   [figwheel-sidecar "0.5.4-3"]
                   [com.cemerick/piggieback "0.2.1"]
                   [org.clojure/test.check "0.9.0"]
                   [binaryage/devtools "0.8.3"]]
    :plugins      [[lein-figwheel "0.5.4-3"]
                   [lein-doo "0.1.7"]
                   ]
    :source-paths ["dev"]
    }
   :uberjar {:aot [weather-report.core]}
   :browser {:test-paths ^:replace ["test/browser"]}}

  :cljsbuild
  {:builds
   [{:id           "dev"
     :source-paths ["src/cljs" "src/cljc"]
     :figwheel     {:on-jsload "weather-report.core/mount-root"}
     :compiler     {:main                 weather-report.core
                    :output-to            "resources/public/js/compiled/app.js"
                    :output-dir           "resources/public/js/compiled/out"
                    :asset-path           "js/compiled/out"
                    :source-map-timestamp true}}

    {:id           "min"
     :source-paths ["src/cljs" "src/cljc"]
     :compiler     {:main            weather-report.core
                    :output-to       "resources/public/js/compiled/app.js"
                    :optimizations   :advanced
                    :closure-defines {goog.DEBUG false
                                      ;; served on same domain
                                      ;; weather-report.core/api-uri "/api"
                                      ;; weather-report.core/api-uri "http://qc-rolodex1.ep.gdi/api"
                                      weather-report.core/api-uri "http://localhost:8080/api"
                                      }
                    :pretty-print    false}}
    {:id           "test"
     :source-paths ["src/cljs" "src/cljc" "test/cljs"]
     :compiler     {:output-to     "resources/public/js/compiled/test.js"
                    :main          weather-report.runner
                    :optimizations :none}}
    ]}

  )
