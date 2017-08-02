(ns charge-box-service.settings
  (:require [cprop.core :refer [load-config]]))

(def config (load-config))