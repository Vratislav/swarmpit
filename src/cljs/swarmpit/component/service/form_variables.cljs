(ns swarmpit.component.service.form-variables
  (:require [material.component.form :as form]
            [material.component.list-table-form :as list]
            [swarmpit.component.state :as state]
            [rum.core :as rum]))

(enable-console-print!)

(def cursor [:form :variables])

(def headers [{:name  "Name"
               :width "35%"}
              {:name  "Value"
               :width "35%"}])

(def empty-info
  (form/value "No environment variables defined for the service."))

(defn- form-name [value index]
  (list/textfield
    {:name     (str "form-name-text-" index)
     :key      (str "form-name-text-" index)
     :value    value
     :onChange (fn [_ v]
                 (state/update-item index :name v cursor))}))

(defn- form-value [value index]
  (list/textfield
    {:name     (str "form-value-text-" index)
     :key      (str "form-value-text-" index)
     :value    value
     :onChange (fn [_ v]
                 (state/update-item index :value v cursor))}))

(defn- render-variables
  [item index]
  (let [{:keys [name
                value]} item]
    [(form-name name index)
     (form-value value index)]))

(defn- form-table
  [variables]
  (list/table headers
              variables
              nil
              render-variables
              (fn [index] (state/remove-item index cursor))))

(defn- add-item
  []
  (state/add-item {:name  ""
                   :value ""} cursor))

(rum/defc form-create < rum/reactive []
  (let [variables (state/react cursor)]
    (if (not (empty? variables))
      (form-table variables))))

(rum/defc form-update < rum/reactive []
  (let [variables (state/react cursor)]
    (if (empty? variables)
      empty-info
      (form-table variables))))