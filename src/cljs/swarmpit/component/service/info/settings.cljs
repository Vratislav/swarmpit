(ns swarmpit.component.service.info.settings
  (:require [material.component.form :as form]
            [swarmpit.docker-utils :as utils]
            [rum.core :as rum]))

(enable-console-print!)

(rum/defc form < rum/static [service]
  (let [image-digest (get-in service [:repository :imageDigest])
        stack (:stack service)]
    [:div.form-service-view-group
     (form/section "General settings")
     (form/item "ID" (:id service))
     (if (some? stack)
       (form/item "STACK" stack))
     (form/item "NAME" (utils/trim-stack stack (:serviceName service)))
     (form/item-date "CREATED" (:createdAt service))
     (form/item-date "LAST UPDATE" (:updatedAt service))
     (form/item "IMAGE" (get-in service [:repository :image]))
     (when (some? image-digest)
       (form/item "IMAGE DIGEST" image-digest))
     (form/item "MODE" (:mode service))]))