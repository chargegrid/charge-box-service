(ns charge-box-service.groups
  (:require [compojure.api.sweet :refer :all]
            [ring.util.http-response :refer :all]
            [schema.core :as s]
            [charge-box-service.persistence.queries :as q]
            [charge-box-service.schema :as schema]
            [charge-box-service.persistence.entities :refer [groups charge-boxes-groups]]
            [ring.util.http-response :refer [created bad-request ok not-found]]
            [korma.core :as k]
            [clojure.tools.logging :as log]
            [schema.core :as s])
  (:import (java.util UUID)))

(defn uuid [] (UUID/randomUUID))

(defn insert [tenant-id group]
  (let [id (uuid)
        input (assoc group :id id :tenant_id tenant-id)]
    (k/insert groups (k/values input))
    input))

(defn create [tenant-id {:keys [name] :as group}]
  (let [existing (q/groups-by-name tenant-id name)]
    (if (empty? existing)
      (created (insert tenant-id group))
      (bad-request {:error (str "A group with the name '" name "' already exists")
                    :code  "DUPLICATE"}))))

(defn get-group [tenant-id group-id]
  (if-let [group (q/group-by-id tenant-id group-id)]
    (ok group)
    (not-found {:error "This group does not exist"})))

(defn list-groups [tenant-id]
  (ok (q/groups-by-tenant tenant-id)))

(defn update-assignments [tenant-id group-id boxes update-fn]
  "Checks if the group & all boxes exist in DB, if so, it runs update-fn"
  (let [group (q/group-by-id tenant-id group-id)
        in-db (q/boxes-by-ids tenant-id (map :id boxes))
        missing? (not= (count in-db) (count boxes))]
    (cond
      (empty? boxes) (bad-request {:error "At least one charge box id should be specified"})
      (nil? group) (not-found {:error "The specified group does not exist"})
      missing? (bad-request {:error "Some or all of the specified charge box id's do not exist"})
      :else (update-fn))))


(defn delete-assignments [group-id boxes]
  (k/delete charge-boxes-groups
            (k/where {:charge_box_id [in (map :id boxes)]
                      :group_id      group-id})))

(defn unassign [tenant-id group-id boxes]
  (update-assignments tenant-id group-id boxes
                      (fn []
                        (delete-assignments group-id boxes)
                        (ok {:message "The charge boxes are unassigned"}))))

(defn existing-assignments [group-id boxes]
  (k/select charge-boxes-groups
            (k/where {:charge_box_id [in (map :id boxes)]
                      :group_id      group-id})))

(defn insert-assignments [group-id boxes]
  (let [values (map #(hash-map :charge_box_id (:id %)
                               :group_id group-id)
                    boxes)]
    (k/insert charge-boxes-groups (k/values values))))

(defn assign [tenant-id group-id boxes]
  (update-assignments tenant-id group-id boxes
                      (fn []
                        (let [existing (existing-assignments group-id boxes)]
                          (if (empty? existing)
                            (do (insert-assignments group-id boxes)
                                (ok {:message "The charge boxes are now assigned"}))
                            (bad-request {:error "Some of the charge boxes are already assigned to the group"}))))))

(def api-routes
  (routes
    (context "/groups" []
             :header-params [x-tenant-id :- s/Uuid]
             (POST "/" _
                   :body [group schema/Group]
                   (create x-tenant-id group))
             (GET "/" _
                  (list-groups x-tenant-id))
             (GET "/:group-id" [group-id]
                  :path-params [group-id :- s/Uuid]
                  (get-group x-tenant-id group-id))
             (POST "/:group-id/charge-boxes" [group-id]
                   :path-params [group-id :- s/Uuid]
                   :body [boxes [{:id s/Uuid}]]
                   (assign x-tenant-id group-id boxes))
             (DELETE "/:group-id/charge-boxes" [group-id]
                     :path-params [group-id :- s/Uuid]
                     :body [boxes [{:id s/Uuid}]]
                     (unassign x-tenant-id group-id boxes)))))
