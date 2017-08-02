(ns charge-box-service.queue
  (:require [langohr.core :as rmq]
            [langohr.channel :as lch]
            [langohr.basic :as lb]
            [langohr.consumers :as lc]
            [charge-box-service.settings :refer [config]]
            [clojure.data.json :as json]
            [charge-box-service.schema :as schema]
            [charge-box-service.boxes :refer [update-for-connect update-for-disconnect]]
            [clojurewerkz.support.json]
            [clojure.tools.logging :as log]
            [charge-box-service.evses :as evses]
            [camel-snake-kebab.extras :refer [transform-keys]]
            [camel-snake-kebab.core :refer [->kebab-case ->snake_case_string]])
  (:import (java.net InetAddress)))

;; Send and receive messages

(defn publish-session-with-evse [channel msg]
  (let [conf (:amqp config)
        exchange (:sessions-exchange conf)
        routing-key (:sessions-evse-routing-key conf)]
    (lb/publish channel exchange routing-key
                (json/write-str msg :key-fn ->snake_case_string)
                {:content-type "application/json" :persistent true})))

(defn handle-session [channel meta ^bytes payload]
  (let [data (-> (String. payload "UTF-8")
                 (json/read-str :key-fn keyword)
                 schema/coerce-session)
        session (transform-keys ->kebab-case data)]
    (if-let [error (:error data)]
      (log/error "Cannot parse session " (pr-str error))
      (if-let [session-with-evse (evses/add-evse-to-session session)]
        (do (log/info "Retrieved EVSE-ID for session: " (pr-str session-with-evse))
            (publish-session-with-evse channel session-with-evse))
        (log/error "Could not retrieve EVSE-ID for session " (pr-str session))))))

(defn handle-conn-event [channel meta ^bytes payload]
  (let [data (-> (String. payload "UTF-8")
                 (json/read-str :key-fn keyword)
                 schema/coerce-conn-event)
        conn-event (transform-keys ->kebab-case data)
        {:keys [event-type charge-box-serial tenant-id occurred-at ip]} conn-event]
    (case event-type
      :connected (update-for-connect charge-box-serial tenant-id occurred-at (InetAddress/getByName ip))
      :disconnected (update-for-disconnect charge-box-serial tenant-id occurred-at (InetAddress/getByName ip)))))

(defn shutdown [ch conn]
  (rmq/close ch)
  (rmq/close conn))

(defn setup []
  (let [conf (:amqp config)
        raw-sessions-queue (:raw-sessions-queue-name conf)
        conn-events-queue (:connection-events-queue-name conf)
        connection (rmq/connect {:uri (:url conf)})
        channel (lch/open connection)]
    (lc/subscribe channel raw-sessions-queue handle-session {:auto-ack true})
    (lc/subscribe channel conn-events-queue handle-conn-event {:auto-ack true})
    (.addShutdownHook (Runtime/getRuntime)
                      (Thread. #(shutdown channel connection)))))
