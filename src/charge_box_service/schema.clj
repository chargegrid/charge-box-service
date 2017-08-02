(ns charge-box-service.schema
  (:require [schema.core :as s]
            [korma.core :refer :all]
            [schema.coerce :as coerce]
            [ring.swagger.coerce :refer [coercer]])
  (:import (org.joda.time DateTime)))

;; taken from https://github.com/e-clearing-net/OCHP/blob/master/OCHP.md#regular-expression-for-evse-id-validation
(def EvseIdRegEx
  #"[A-Z]{2}\*?[A-Z0-9]{3}\*?[E][A-Z0-9][A-Z0-9*]{0,30}")

(def EvseId (s/pred #(re-matches EvseIdRegEx %)))

(s/defschema Group
  {:name        s/Str
   :description s/Str})

(s/defschema Evse
  {:id              EvseId                                  ;; EVSE-ID as formatted in the open standard
   :connector_id    s/Int                                   ;; connector-id (as identified via OCPP)
   :current_type    (s/enum :AC :DC)
   :max_power_watts s/Int})

(s/defschema Socket
  {:type_id                          s/Str
   :cable_attached                   s/Bool
   (s/optional-key :max_power_watts) s/Int})

(s/defschema EvseWithSockets
  {:id              EvseId                                  ;; EVSE-ID as formatted in the open standard
   :connector_id    s/Int                                   ;; connector-id (as identified via OCPP)
   :current_type    (s/enum :AC :DC)
   :max_power_watts s/Int
   :sockets         [Socket]})

(s/defschema ChargeBoxRegistration
  {:location                        {:latitude  s/Num
                                     :longitude s/Num}
   :address_line_1                  s/Str
   (s/optional-key :address_line_2) s/Str
   :city                            s/Str
   :country_iso                     s/Str
   :evses                           [EvseWithSockets]})

(s/defschema Session
  {:charge_box_serial String
   :connector_id      s/Int
   :started_at        DateTime
   :ended_at          DateTime
   :volume            s/Num
   :user_id           String
   :tenant_id         s/Uuid})

(s/defschema ConnectionEvent
  {:event_type        s/Keyword
   :occurred_at       DateTime
   :tenant_id         s/Uuid
   :charge_box_serial String
   :ip                String
   :status            (s/maybe String)})

(def coerce-session
  (coerce/coercer Session (coercer :json)))

(def coerce-conn-event
  (coerce/coercer ConnectionEvent (coercer :json)))
