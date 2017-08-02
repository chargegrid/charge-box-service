(ns charge-box-service.persistence.db
  (:require [korma.db :as korma]
            [ragtime.jdbc :as jdbc]
            [ragtime.core :refer [migrate-all into-index]]
            [clojure.tools.logging :as log]
            [charge-box-service.settings :refer [config]]
            [perseverance.core :as p])
  (:import (org.postgresql.util PSQLException)))

(def db-spec-string
  (let [conf (:database config)]
    (str
      "jdbc:postgresql://"
      (:user conf)
      ":"
      (:password conf)
      "@"
      (:host conf)
      ":"
      (:port conf)
      "/"
      (:name conf))))

(defn migrate []
  (let [store (jdbc/sql-database db-spec-string)
        migrations (jdbc/load-resources "migrations")
        index (into-index migrations)]
    (log/info "Running migrations if need be...")
    (migrate-all store index migrations)))

(defn attempt-migrate []
  (p/retriable {:catch [PSQLException]} (migrate)))

; Setup korma
(korma/defdb db db-spec-string)
