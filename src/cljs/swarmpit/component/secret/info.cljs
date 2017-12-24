(ns swarmpit.component.secret.info
  (:require [material.component :as comp]
            [material.component.form :as form]
            [material.component.panel :as panel]
            [material.icon :as icon]
            [swarmpit.url :refer [dispatch!]]
            [swarmpit.component.state :as state]
            [swarmpit.component.mixin :as mixin]
            [swarmpit.component.handler :as handler]
            [swarmpit.component.message :as message]
            [swarmpit.component.progress :as progress]
            [swarmpit.routes :as routes]
            [rum.core :as rum]))

(enable-console-print!)

(def cursor [:form])

(defn- secret-handler
  [secret-id]
  (handler/get
    (routes/path-for-backend :secret {:id secret-id})
    {:on-success (fn [response]
                   (state/set-value response cursor))}))

(defn- delete-secret-handler
  [secret-id]
  (handler/delete
    (routes/path-for-backend :secret-delete {:id secret-id})
    {:on-success (fn [_]
                   (dispatch!
                     (routes/path-for-frontend :secret-list))
                   (message/info
                     (str "Secret " secret-id " has been removed.")))
     :on-error   (fn [response]
                   (message/error
                     (str "Secret removing failed. Reason: " (:error response))))}))

(def mixin-init-form
  (mixin/init-form
    (fn [{{:keys [id]} :params}]
      (secret-handler id))))

(rum/defc form-info < rum/static [secret]
  [:div
   [:div.form-panel
    [:div.form-panel-left
     (panel/info icon/secrets
                 (:secretName secret))]
    [:div.form-panel-right
     (comp/mui
       (comp/raised-button
         {:onTouchTap #(delete-secret-handler (:id secret))
          :label      "Delete"}))]]
   [:div.form-view
    [:div.form-view-group
     (form/item "ID" (:id secret))
     (form/item "NAME" (:secretName secret))
     (form/item-date "CREATED" (:createdAt secret))
     (form/item-date "UPDATED" (:updatedAt secret))]]])

(rum/defc form < rum/reactive
                 mixin-init-form
                 mixin/subscribe-form [_]
  (let [secret (state/react cursor)]
    (progress/form
      (nil? secret)
      (form-info secret))))