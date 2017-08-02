(ns charge-box-service.persistence.entities
  (:require [korma.core :refer :all])
  (:import (org.postgresql.util PGobject)))

(declare evses charge-boxes groups sockets socket-types)

;; PG enums are PGObjects
(defn kw->pgobject
  [kw type]
  (doto (PGobject.)
    (.setType type)
    (.setValue (name kw))))

(defn modify
  "Return record with field modified by fn if field exists, otherwise return record"
  [record field fn]
  (if (field record)
    (assoc record field (fn (field record)))
    record))

(defentity evses
           (transform #(modify % :current_type keyword))
           (prepare (fn [evse]
                      (modify evse :current_type #(kw->pgobject % "current_type")))))

(defentity charge-boxes
           (table :charge_boxes)
           (has-many evses))

(defentity unregistered-charge-boxes
           (table :unregistered_charge_boxes))

(defentity charge-boxes-groups
           (table :charge_boxes_groups))

(defentity groups
           (has-many charge-boxes))

(defentity sockets
           (belongs-to evses)
           (has-one socket-types))

(defentity socket-types
           (table :socket_types))
