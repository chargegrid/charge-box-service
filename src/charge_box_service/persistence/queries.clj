(ns charge-box-service.persistence.queries
  (:require [charge-box-service.persistence.db :refer :all]
            [charge-box-service.persistence.entities :refer :all]
            [korma.core :refer :all]
            [korma.db :refer [transaction]]
            [clj-time.jdbc]
            [clojure.tools.logging :as log]
            [clojure.java.jdbc :as jdbc])
  (:import (java.util UUID)
           (org.postgresql.util PGobject)
           (java.net InetAddress)))

(extend-protocol jdbc/ISQLValue
  InetAddress
  (sql-value [v]
    (doto (PGobject.)
      (.setType "inet")
      (.setValue (.getHostAddress v)))))

(extend-protocol jdbc/IResultSetReadColumn
  PGobject
  (result-set-read-column [v _2 _3]
    (if (= (.getType v) "inet")
      (InetAddress/getByName (str v))
      (str v))))

;; Scope: tenant-id

(defn groups-for [tenant-id]
  (-> (select* groups)
      (where {:tenant_id tenant-id})))

(defn charge-boxes-for [tenant-id]
  (-> (select* charge-boxes)
      (where {:tenant_id tenant-id})))

(defn unreg-charge-boxes-for [tenant-id]
  (-> (select* unregistered-charge-boxes)
      (where {:tenant_id tenant-id})))

(defn evses-for [tenant-id]
  (-> (select* evses)
      (join :right charge-boxes
            (and (= :charge_boxes.tenant_id tenant-id)
                 (= :charge_boxes.id :evses.charge_box_id)))))

;; GROUPS/select

(defn groups-by-tenant [tenant-id]
  (select (groups-for tenant-id)))

(defn group-by-id [tenant-id id]
  (-> (groups-for tenant-id)
      (where {:id id})
      (select)
      (first)))

(defn groups-by-name [tenant-id name]
  (-> (groups-for tenant-id)
      (where {:name name})
      (select)))

;; CHARGE BOXES/select

(defn box-by-serial [tenant-id serial]
  (-> (charge-boxes-for tenant-id)
      (where {:serial serial})
      (select)
      (first)))

(defn box-by-id [tenant-id id]
  (-> (charge-boxes-for tenant-id)
      (where {:id id})
      (select)
      (first)))

(defn boxes-by-tenant [tenant-id]
  (-> (charge-boxes-for tenant-id)
      (select)))

(defn boxes-by-group [tenant-id group-id]
  (-> (charge-boxes-for tenant-id)
      (join :right charge-boxes-groups
            (and (= :charge_boxes_groups.charge_box_id :charge_boxes.id)
                 (= :charge_boxes_groups.group_id group-id)))
      (select)))

(defn boxes-by-ids [tenant-id box-ids]
  "box-ids contains a vector of UUIDs to filter for"
  (-> (charge-boxes-for tenant-id)
      (where {:id [in box-ids]})
      (select)))

;; CHARGE BOXES/update

(defn update-box-by-serial [serial tenant-id box]
  (update charge-boxes
          (set-fields box)
          (where {:serial serial})
          (where {:tenant_id tenant-id})))

;; EVSES/select
(defn sockets-for-evse [tenant-id evse-id]
  (-> (select* sockets)
      (fields [:type_id :type] :cable_attached :max_power_watts)
      (where {:tenant_id tenant-id})
      (where {:evse_id evse-id})
      (select)))

(defn with-sockets [evses tenant-id]
  (map (fn [evse] (assoc evse :sockets (sockets-for-evse tenant-id (:id evse))))
       evses))

(defn evse-by-id [tenant-id evse-id]
  (-> (evses-for tenant-id)
      (where {:id evse-id})
      (select)
      (with-sockets tenant-id)
      (first)))

(defn evses-by-box [tenant-id box-id]
  (-> (select* evses)
      (where {:charge_box_id box-id})
      (join :right charge-boxes
            (and (= :charge_boxes.tenant_id tenant-id)
                 (= :charge_boxes.id :evses.charge_box_id)))
      (select)
      (with-sockets tenant-id)))

(defn evse-by-box-and-connector [tenant-id box-id connector-id]
  (-> (select* evses)
      (where {:charge_box_id box-id})
      (where {:connector_id connector-id})
      (join :right charge-boxes
            (and (= :charge_boxes.tenant_id tenant-id)
                 (= :charge_boxes.id :evses.charge_box_id)))
      (select)
      (with-sockets tenant-id)
      (first)))

;; SOCKET TYPES/select

(defn sockettypes []
  (select socket-types))

;; UNREGISTERED CHARGE BOXES/select

(defn unreg-boxes-by-tenant [tenant-id]
  (select (unreg-charge-boxes-for tenant-id)))

(defn unreg-box-by-id [tenant-id id]
  (-> (unreg-charge-boxes-for tenant-id)
      (where {:id id})
      (select)
      (first)))

;; UNREGISTERED CHARGE BOXES/insert

(defn create-unreg-box [serial tenant-id last-connected-at last-disconnected-at last-known-ip]
  (let [id (UUID/randomUUID)]
    (insert unregistered-charge-boxes
            (values {:id                   id
                     :serial               serial
                     :tenant_id            tenant-id
                     :last_connected_at    last-connected-at
                     :last_disconnected_at last-disconnected-at
                     :last_known_ip        last-known-ip}))))

;; UNREGISTERED CHARGE BOXES/update

(defn update-unreg-box-by-serial [serial tenant-id box]
  (update unregistered-charge-boxes
          (set-fields box)
          (where {:serial serial})
          (where {:tenant_id tenant-id})))

;; Register CHARGE BOX

(defn register-charge-box [box evse-data socket-data]
  (transaction
    (insert charge-boxes (values box))
    (doseq [evse evse-data]
      (insert evses (values evse)))
    (doseq [socket socket-data]
      (insert sockets (values socket)))
    (delete unregistered-charge-boxes (where {:id (:id box)}))))

