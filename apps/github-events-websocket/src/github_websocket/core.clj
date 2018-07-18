(ns github-websocket.core
  (:require [clojure.core.async :as a]
            [nats.core :as nats]
            [org.httpkit.server :as http]
            [jsonista.core :as json]
            [compojure.handler :refer [site]]
            [compojure.core :refer [defroutes GET context]]))

(defonce nats-connection (atom nil))

(defn sleep-jitter
  [val percent]
  (let [amount (* (/ percent 100) val)
        sleep (+ (- val amount) (rand-int (inc (* 2 amount))))]
    (a/<!! (a/timeout sleep))))

(defn ws-handler
  [request]
  (prn "connect" request)
  (compare-and-set! nats-connection nil (nats/connect (System/getenv "NATS_URL")))
  (let [topic      (-> request :params :topic)
        topic-chan (nats/subscribe @nats-connection topic)]
    (http/with-channel request ws-chan
      (a/go-loop [v (a/<! topic-chan)]
        (when v
          (http/send! ws-chan (json/write-value-as-string v))
          (sleep-jitter 1000 50)
          (recur (a/<! topic-chan))))
      (http/on-close ws-chan (fn [status]
                               (println "channel closed: " status)
                               (a/close! topic-chan))))))

(defroutes all-routes
  (context "/ws/:topic" []
           (GET "/" [] ws-handler)))


(defn -main [& args]
  (http/run-server (site #'all-routes) {:port 8080}))


