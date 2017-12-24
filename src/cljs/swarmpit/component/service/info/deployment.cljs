(ns swarmpit.component.service.info.deployment
  (:require [material.component.form :as form]
            [material.component.list-table-info :as list]
            [rum.core :as rum]))

(enable-console-print!)

(def placement-render-item-keys
  [[:rule]])

(defn- placement-render-item
  [item]
  (val item))

(rum/defc form < rum/static [deployment]
  (let [autoredeploy (:autoredeploy deployment)
        update-delay (get-in deployment [:update :delay])
        update-parallelism (get-in deployment [:update :parallelism])
        update-failure-action (get-in deployment [:update :failureAction])
        rollback-delay (get-in deployment [:rollback :delay])
        rollback-parallelism (get-in deployment [:rollback :parallelism])
        rollback-failure-action (get-in deployment [:rollback :failureAction])
        placement (:placement deployment)
        restart-policy-condition (get-in deployment [:restartPolicy :condition])
        restart-policy-delay (get-in deployment [:restartPolicy :delay])
        restart-policy-attempts (get-in deployment [:restartPolicy :attempts])]
    [:div.form-service-view-group.form-service-group-border
     (form/section "Deployment")
     (form/item "AUTOREDEPLOY" (if autoredeploy
                                      "on"
                                      "off"))
     (if (not-empty placement)
       [:div
        (form/subsection "Placement")
        (list/table-headless placement
                             placement-render-item
                             placement-render-item-keys)])
     (form/subsection "Restart Policy")
     (form/item "CONDITION" restart-policy-condition)
     (form/item "DELAY" (str restart-policy-delay "s"))
     (form/item "MAX ATTEMPTS" restart-policy-attempts)
     (form/subsection "Update Config")
     (form/item "PARALLELISM" update-parallelism)
     (form/item "DELAY" (str update-delay "s"))
     (form/item "ON FAILURE" update-failure-action)
     (if (= "rollback" update-failure-action)
       [:div
        (form/subsection "Rollback Config")
        (form/item "PARALLELISM" rollback-parallelism)
        (form/item "DELAY" (str rollback-delay "s"))
        (form/item "ON FAILURE" rollback-failure-action)])]))