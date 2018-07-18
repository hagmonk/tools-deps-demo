(ns nats.core
  (:require [clojure.core.async :as a]
            [jsonista.core :as json])
  (:import [io.nats.client Nats Subscription Connection Message]
           (java.nio.charset StandardCharsets)
           (java.time Duration)))

(defn- ser
  [o]
  (-> o
      json/write-value-as-string
      (.getBytes StandardCharsets/UTF_8)))

(defn- de
  [^Message msg]
  (-> (.getData msg)
      (String.  StandardCharsets/UTF_8)
      json/read-value
      clojure.walk/keywordize-keys))

(defn connect
  ([]
   (Nats/connect))
  ([url]
   (Nats/connect ^String url)))

(defn close
  [^Connection nats]
  (.close nats))

(defn subscribe
  [^Connection nats topic]
  (let [sub      ^Subscription (.subscribe nats topic)
        out-chan (a/chan)]
    (a/go
      (try
        (loop []
          (when-let [msg (.nextMessage sub (Duration/ofMillis 500))]
            (let [msg-str (de msg)]
              (a/>! out-chan msg-str)))
          (recur))
        (catch Throwable ex
          (prn ex)
          (a/close! out-chan))))
    out-chan))

;; TODO: unsubscribe

(defn publish
  [^Connection nats topic message]
  (.publish nats topic (ser message)))

(comment
  (def ccc (connect))

  (def sub-chan (subscribe ccc "boris"))

  (a/take! sub-chan #(prn "weee" %))

  (publish ccc "boris" {:something "going on here"}))
