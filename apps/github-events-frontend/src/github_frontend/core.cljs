(ns github-frontend.core
  (:require
   [cljsjs.antd]
   [reagent.core :as r]
   [goog.events :as events]
   [goog.string :as gstring]
   [goog.ui.Component]
   [goog.net.ErrorCode]
   [goog.net.EventType]
   [cljs.core.async :refer [<! chan sliding-buffer put! close! timeout]])
  (:require-macros
   [cljs.core.async.macros :refer [go-loop go]])
  (:import (goog.net.WebSocket EventType)
           (goog.net WebSocket)))

(enable-console-print!)

(def ws (WebSocket. true (fn [] 10)))

(defn open [url & {:keys [on-message on-open]}]
  (events/listen ws EventType.OPENED
                 (fn [e]
                   (when on-open
                     (on-open))))
  (events/listen ws EventType.MESSAGE
                 (fn [e]
                   (when on-message
                     (on-message (.-message e)))))
  (events/listen ws EventType.CLOSED
                 (fn [e]
                   #_(.log js/console "Websocket closed.")))
  (events/listen ws EventType.ERROR
                 (fn [e]
                   #_(.log js/console (str "Websocket error" e))))
  (.open ws url))

(defn send [command message]
  (.send ws (pr-str [command message])))

(def antd js/antd)

(def timeline-items (r/atom []))

(defn connect-socket []
  (open "ws://localhost:8080/ws/github-events"
        :on-message
        (fn [msg]
          (swap! timeline-items conj msg))))

(defn main-template []
  [:div {:style {:background-color "white"}}
   [:> antd.Timeline
    (for [i @timeline-items]
      ^{:key i} [:> antd.Timeline.Item {:color "green"} i])]])

(defn main-panel []
  [:> antd.Layout {:style {:height "100vh"}}
   [:> antd.Layout.Content {:style {:margin 0 :padding 12 :border "1px solid black"}}
    [main-template]]])

(connect-socket)
(r/render [main-panel] (js/document.getElementById "app"))

