(ns bones.client
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]
                   [schema.core :as s])
  (:require [cljs-http.client :as http]
            [cljs.core.async :as a]))

(defn command [data token]
  ;; maybe validate :command, :args present
  (go
    (let [url "http://localhost:8080/api/command"
          req {:edn-params data
               :headers {"authorization" (str "Token " token)}
               }
          resp (<! (http/post url req))]
      resp)))

(defn login [data]
  (go
    (let [url "http://localhost:8080/api/login"
          req {:edn-params data}
          resp (<! (http/post url req))]
      resp)))

(defn query [data token]
  ;; maybe validate :query, :args present
  (go
    (let [url "http://localhost:8080/api/query"
          req {:query-params data
               :headers {"authorization" (str "Token " token)}}
          resp (<! (http/get url req))]
      resp)))

(comment
  #_(a/take! (command {:command :echo :args {:hello "mr"}}
                    (get-in @re-frame.core/db [:bones/token])))
  #_(a/take! (post "http://localhost:8080/api/login"
                 {:command :login
                  :args {:username "abc" :password "xyz"}} )
           println)
  )

(defn channels []
  (atom {"onopen" (a/chan)
         "onerror" (a/chan (a/dropping-buffer 10))
         "onmessage" (a/chan 100) ;;notsureifwanttoblockconnection
         "onclose" (a/chan)
         "send" (a/chan 10)
         "close" (a/chan)}))

(defonce conn (channels))

(defn listen [url {:keys [:constructor] :or {constructor js/WebSocket. }}]
  "binds a js/websocket to a core-async channel event bus"
  (let [websocket (constructor url)
        webbus (a/chan)
        event-bus (a/pub webbus first)
        publish! #(a/put! webbus [%1 %2])
        subscribe! #(a/sub event-bus %1 (get @conn %1))]
    (doto websocket
      (aset "binaryType" "arraybuffer") ;;notsureifrequired
      (aset "onopen"    #(publish! "onopen" "open!"))
      (aset "onerror"   #(publish! "onerror" %))
      (aset "onmessage" #(publish! "onmessage" %))
      (aset "onclose"   #(do (publish! "onclose" "closed!")
                             (swap! conn dissoc "event-bus")
                             (a/close! webbus))))
    (a/sub event-bus "send" (get @conn "send"))
    (a/sub event-bus "close" (get @conn "close"))
    (go-loop []
        (let [msg (a/<! (get @conn "send"))]
          #(.send websocket %)
          (recur)))
    (go-loop []
        (let [msg (a/<! (get @conn "close"))]
          #(.close websocket %)))

    (subscribe! "onopen")
    (subscribe! "onerror")
    (subscribe! "onmessage")
    (subscribe! "onclose")
    (swap! conn assoc "event-bus" event-bus)
    websocket))
