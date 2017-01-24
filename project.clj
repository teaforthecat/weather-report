(defproject weather-report "0.2.0"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.9.229"]

                 [com.stuartsierra/component "0.3.1"]
                 ;; bones deps
                 [bones/conf "0.2.2"]
                 [bones/http "0.2.5"]
                 ;; bones.stream
                 [com.taoensso/carmine "2.12.2"]
                 [org.onyxplatform/onyx-kafka "0.8.8.0"]
                 [manifold "0.1.4"]

                 ;; domain specific
                 [org.clojars.pntblnk/clj-ldap "0.0.12"]

                 ;; bones cljs
                 ;; [reagent "0.6.0"]
                 ;; maybe?
                 [reagent "0.6.0" :exclusions [cljsjs/react]]
                 [cljsjs/react-with-addons "15.2.1-0"]
                 [reagent-forms "0.5.28"]
                 [re-frame "0.9.0"]
                 [secretary "1.2.3"]
                 [bones/client "0.2.3"]
                 [bones/editable "0.1.2"]

                 ]

  :plugins [[lein-cljsbuild "1.1.3"]]

  :min-lein-version "2.5.3"

  :resource-paths ["resources"]
  :source-paths ["src/clj" "src/cljs" "src/cljc"]
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
                   [com.cemerick/piggieback "0.2.1"]
                   [binaryage/devtools "0.8.3"]]
    :plugins      [[lein-figwheel "0.5.4-3"]
                   [lein-doo "0.1.7"]
                   ]
    :source-paths ["dev"]
    }
   :uberjar {:aot [weather-report.core]}}

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
                    :closure-defines {goog.DEBUG false}
                    :pretty-print    false}}
    {:id           "test"
     :source-paths ["src/cljs" "src/cljc" "test/cljs"]
     :compiler     {:output-to     "resources/public/js/compiled/test.js"
                    :main          weather-report.runner
                    :optimizations :none}}
    ]}

  )
