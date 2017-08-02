(ns charge-box-service.evses
  (:require [compojure.api.sweet :refer :all]
            [charge-box-service.schema :as schema]
            [charge-box-service.persistence.entities :refer [evses]]
            [schema.core :as s]
            [ring.util.http-response :refer [created ok bad-request not-found]]
            [korma.core :as k]
            [charge-box-service.persistence.queries :as q]
            [clojure.tools.logging :as log]))

(defn insert [tenant-id box-id evse]
  (let [input (assoc evse :charge_box_id box-id
                          :tenant_id tenant-id)]
    (k/insert evses (k/values input))
    input))

(defn create [tenant-id box-id {evse-id :id connector-id :connector_id :as evse}]
  (let [existing-evse (q/evse-by-id tenant-id evse-id)
        existing-connector (q/evse-by-box-and-connector tenant-id box-id connector-id)
        box (q/box-by-id tenant-id box-id)]
    (cond
      existing-evse (bad-request {:error (str "An EVSE with the id '" evse-id
                                              "' already exists")
                                  :code  "DUPLICATE"})
      existing-connector (bad-request {:error (str "An EVSE with box '" box-id
                                                   "' and connector '" connector-id
                                                   "' already exists")
                                       :code  "CONNECTOR_ID_EXISTS"})
      (nil? box) (not-found {:error "The specified charge box does not exist"})
      :else (created (insert tenant-id box-id evse)))))

(defn get-evse [tenant-id evse-id]
  "Get EVSE object by id + tenant id"
  (if-let [evse (q/evse-by-id tenant-id evse-id)]
    (ok evse)
    (not-found {:error "This EVSE does not exist"})))

(defn list-in-box [tenant-id box-id]
  (if-not (empty? (q/box-by-id tenant-id box-id))
    (ok (q/evses-by-box tenant-id box-id))
    (not-found {:error "The specified charge box does not exist"})))

(defn list-socket-types []
  (ok (q/sockettypes)))

(def api-routes
  (routes
    (context "" []
      :header-params [x-tenant-id :- s/Uuid]
      (POST "/charge-boxes/:box-id/evses" [box-id]
        :path-params [box-id :- s/Uuid]
        :body [evse schema/Evse]
        (create x-tenant-id box-id evse))
      (GET "/charge-boxes/:box-id/evses" []
        :path-params [box-id :- s/Uuid]
        (list-in-box x-tenant-id box-id))
      (GET "/evses/socket-types" []
        (list-socket-types))
      (GET "/evses/:evse-id" [evse-id]
        :path-params [evse-id :- schema/EvseId]
        (get-evse x-tenant-id evse-id)))))

(defn add-evse-to-session [{:keys [tenant-id charge-box-serial connector-id] :as session}]
  (if-let [box (q/box-by-serial tenant-id charge-box-serial)]
    (if-let [evse (q/evse-by-box-and-connector tenant-id (:id box) connector-id)]
      (let [clean-session (dissoc session :connector-id :charge-box-serial)]
        (assoc clean-session :evse-id (:id evse)))
      (log/error "The EVSE could not been found"))
    (log/error "The charge box could not been found")))
