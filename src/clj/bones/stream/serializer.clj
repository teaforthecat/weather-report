(ns bones.stream.serializer
  (:require [cognitect.transit :as transit])
  (:import [java.io ByteArrayInputStream ByteArrayOutputStream]))

(defn encoder [data-format]
  (fn [data]
    (.toByteArray
     (let [buf (ByteArrayOutputStream. 4096)
           writer (transit/writer buf data-format)]
       ;; write to writer, but return buffer
       (transit/write writer data)
       buf))))

(defn decoder [data-format]
  (fn [bytes]
    (-> bytes
        (ByteArrayInputStream.)
        (transit/reader data-format)
        (transit/read))))

(comment
  ((transit-decoder :json)
   ((transit-encoder :json) "hello"))
)
