(ns github-events.core
  (:require [github-api.core :as github]
            [clojure.core.async :as a]
            [nats.core :as nats]))

(defn publish-loop
  []
  (let [conn (nats/connect (System/getenv "NATS_URL"))
        chan (a/chan 100)
        events (github/api-get chan [:events])]
    (prn "starting publish loop")
    (loop [event (a/<!! chan)]
      (when event
        (nats/publish conn "github-events" event)
        (recur (a/<!! chan))))))

(defn -main [& args]
  (publish-loop))


