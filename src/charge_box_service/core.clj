(ns charge-box-service.core
  (:require [camel-snake-kebab.core :refer [->snake_case_string]]
            [charge-box-service.settings :refer [config]]
            [ring.middleware.logger :refer [wrap-with-logger]]
            [ring.middleware.reload :refer [wrap-reload]]
            [org.httpkit.server :refer [run-server]]
            [compojure.api.sweet :refer [defapi defroutes]]
            [charge-box-service.persistence.db :as db]
            [clojure.tools.logging :as log]
            [charge-box-service.boxes :as boxes]
            [charge-box-service.groups :as groups]
            [charge-box-service.evses :as evses]
            [charge-box-service.queue :as queue]
            [cheshire.generate :refer [add-encoder]]
            [perseverance.core :as p])
  (:gen-class)
  (:import (java.net InetAddress)
           (com.fasterxml.jackson.core JsonGenerator)))

(def options {:format
              {:formats [:json]
               :response-opts
                        {:json
                         {:key-fn ->snake_case_string}}}})

(defn encode-inet-address
  "Encode a InetAddress for the json generator."
  [^InetAddress ip ^JsonGenerator jg]
  (.writeString jg (.getHostAddress ip)))

(add-encoder InetAddress encode-inet-address)

(defroutes api-routes
  #'groups/api-routes
  #'boxes/api-routes
  #'evses/api-routes)

(defapi app options
            (-> api-routes
                wrap-with-logger
                wrap-reload))

(defn -main [& args]
  (p/retry {} (db/attempt-migrate))
  (queue/setup)
  (let [port (or (:port config) 8083)]
    (run-server app {:port port})
    (log/info "Server running at port" port)))
