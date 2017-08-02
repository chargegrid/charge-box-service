(ns charge-box-service.boxes
  (:require [compojure.api.sweet :refer :all]
            [charge-box-service.schema :as schema]
            [schema.core :as s]
            [ring.util.http-response :refer [created ok bad-request not-found]]
            [korma.core :as k]
            [charge-box-service.persistence.queries :as q]
            [clojure.tools.logging :as log]
            [clj-time.core :as t]
            [clojure.string :as str]))

(defn- map-evse [evse box-id tenant-id]
  (let [stripped (dissoc evse :sockets)]
    (assoc stripped :charge_box_id box-id :tenant_id tenant-id)))

(defn- map-socket [socket evse-id tenant-id]
  (assoc socket :tenant_id tenant-id :evse_id evse-id))

(defn- map-sockets [{:keys [id sockets]} tenant-id]
  (map #(map-socket % id tenant-id) sockets))

(defn register-box [tenant-id box-id {:keys [location evses] :as registration}]
  (let [unreg-box (q/unreg-box-by-id tenant-id box-id)]
    (if (some? unreg-box)
      (let [box (dissoc registration :location :evses)
            gps-lat (:latitude location)
            gps-lon (:longitude location)
            cbs-merged (merge unreg-box box)
            complete-box (assoc cbs-merged :gps_lat gps-lat :gps_lon gps-lon)
            stripped-evses (map #(map-evse % box-id tenant-id) evses)
            sockets (flatten (map #(map-sockets % tenant-id) evses))]
        (q/register-charge-box complete-box stripped-evses sockets)
        (created complete-box))
      (not-found {:error (str "No unregistered charge box with id '" box-id "' exists")
                  :code  "NOT_FOUND"}))))

(defn update-for-connect [serial tenant-id last-connected-at last-known-ip]
  (when (< (q/update-box-by-serial serial tenant-id {:last_connected_at last-connected-at
                                                     :last_known_ip     last-known-ip}) 1)
    (when (< (q/update-unreg-box-by-serial serial tenant-id {:last_connected_at last-connected-at
                                                             :last_known_ip     last-known-ip}) 1)
      (q/create-unreg-box serial tenant-id last-connected-at nil last-known-ip))))

(defn update-for-disconnect [serial tenant-id last-disconnected-at last-known-ip]
  (when (< (q/update-box-by-serial serial tenant-id {:last_disconnected_at last-disconnected-at
                                                     :last_known_ip        last-known-ip}) 1)
    (when (< (q/update-unreg-box-by-serial serial tenant-id {:last_disconnected_at last-disconnected-at
                                                             :last_known_ip        last-known-ip}) 1)
      (q/create-unreg-box serial tenant-id nil last-disconnected-at last-known-ip))))

(defn- is-connected? [box]
  (let [last-connected-at (:last_connected_at box)
        last-disconnected-at (:last_disconnected_at box)]
    (cond
      (nil? last-connected-at) false
      (and (some? last-connected-at) (nil? last-disconnected-at)) true
      (t/after? last-connected-at last-disconnected-at) true
      :else false)))

(defn- add-connected [box]
  (assoc box :is-connected (is-connected? box)))

(defn get-box [tenant-id box-id]
  (if-let [box (q/box-by-id tenant-id box-id)]
    (ok (add-connected box))
    (not-found {:error "This charge box does not exist"})))

(defn get-unreg-box [tenant-id box-id]
  (if-let [box (q/unreg-box-by-id tenant-id box-id)]
    (ok (add-connected box))
    (not-found {:error "This unregistered charge box does not exist"})))

(defn with-evses [boxes tenant-id]
  (map (fn [box] (assoc box :evses (q/evses-by-box tenant-id (:id box))))
       boxes))


(defn list-boxes [tenant-id include]
  (let [boxes (map add-connected (q/boxes-by-tenant tenant-id))]
    (if (and true (some #(= "evses" %) include))
      (ok (with-evses boxes tenant-id))
      (ok boxes))))

(defn list-unregistered-boxes [tenant-id]
  (ok (map add-connected (q/unreg-boxes-by-tenant tenant-id))))

(defn list-in-group [tenant-id group-id]
  (if-not (empty? (q/group-by-id tenant-id group-id))
    (ok (map add-connected (q/boxes-by-group tenant-id group-id)))
    (not-found {:error "The specified group does not exist"
                :code  "NOT_FOUND"})))

(def api-routes
  (routes
    (context "" []
      :header-params [x-tenant-id :- s/Uuid]
      (GET "/charge-boxes" []
        :query-params [{include :- s/Str ""}]
        (list-boxes x-tenant-id (str/split include #",")))
      (GET "/charge-boxes/unregistered" []
        (list-unregistered-boxes x-tenant-id))
      (GET "/charge-boxes/unregistered/:box-id" [box-id]
        :path-params [box-id :- s/Uuid]
        (get-unreg-box x-tenant-id box-id))
      (POST "/charge-boxes/unregistered/:box-id/register" [box-id]
        :path-params [box-id :- s/Uuid]
        :body [registration schema/ChargeBoxRegistration]
        (register-box x-tenant-id box-id registration))
      (GET "/charge-boxes/:box-id" [box-id]
        :path-params [box-id :- s/Uuid]
        (get-box x-tenant-id box-id))
      (GET "/groups/:group-id/charge-boxes" [group-id]
        :path-params [group-id :- s/Uuid]
        (list-in-group x-tenant-id group-id)))))
