(ns github-api.core
  (:require [org.httpkit.client :as http]
            [jsonista.core :as json]
            [clojure.string :as str]
            [clojure.core.async :as a]
            [github-api.links :refer :all]))

(def token (System/getenv "GITHUB_TOKEN"))
(def api-root (System/getenv "GITHUB_API"))

(defn api-path
  [p]
  (let [uri-path (str/join "/" (map #(if (keyword? %) (name %) (str %)) p))]
    (str api-root uri-path)))

(defn- get-impl
  [chan url]
  (let [headers (cond-> {"Authorization" (str "token " token)})]
    (prn "fetching" url)
    (http/get
     url
     {:headers headers}
     (fn [{:keys [status error] :as response}]
       (try
         (cond
           error
           (a/put! chan (Throwable->map error) (fn [_] (a/close! chan)))

           (<= 200 status 299)
           (a/put! chan
                    (update response :body (comp clojure.walk/keywordize-keys json/read-value))
                    (fn [_] (if-some [next-link (some-> response links-response :next :href)]
                              (get-impl chan next-link)
                              (a/close! chan))))

           :else
           (a/put! chan response (fn [_] (a/close! chan))))
         (catch Throwable ex
           (a/put! chan (Throwable->map ex) (fn [_] (a/close! chan)))))))
    chan))

(defmulti api-get
  (fn [chan path & extra]
    (cond
      (some #{:events} path) :events
      :else                  nil)))

(defmethod api-get :events
  ([chan path]
   (api-get chan path {}))
  ([chan path extra-headers]
   (let [url      (api-path path)
         seen-ids (atom #{})]
     (a/go-loop [output (get-impl (a/chan 10 (mapcat :body)) url)]
       (if-let [v (a/<! output)]
         (if-let [id (:id v)]
           (if (get seen-ids (:id v))
             (recur output)
            (do
              (a/>! chan v)
              (swap! seen-ids conj (:id v))
              (recur output)))
           (a/put! chan v (fn [_] (a/close! chan))))
         (do
          (a/<! (a/timeout 60000))
          (recur (get-impl (a/chan 10 (mapcat :body)) url)))))
     chan)))

(comment
  (def out-chan (a/chan))

  (api-get out-chan [:events])

  (loop [thing (a/<!! out-chan)]
    (when thing
      (prn thing)
      (recur (a/<!! out-chan)))))
