(ns swarmpit.component.service.log
  (:require [material.icon :as icon]
            [material.component :as comp]
            [material.component.panel :as panel]
            [swarmpit.component.state :as state]
            [swarmpit.component.mixin :as mixin]
            [swarmpit.component.handler :as handler]
            [swarmpit.routes :as routes]
            [clojure.string :as string]
            [sablono.core :refer-macros [html]]
            [rum.core :as rum]))

(def cursor [:form])

(defn- auto-scroll!
  []
  (when (true? (:autoscroll (state/get-value cursor)))
    (let [el (.getElementById js/document "service-log")]
      (set! (.-scrollTop el)
            (.-scrollHeight el)))))

(defn- filter-items
  [items predicate]
  (filter #(string/includes? (:line %) predicate) items))

(defn- service-handler
  [service-id]
  (handler/get
    (routes/path-for-backend :service {:id service-id})
    {:on-success (fn [response]
                   (state/update-value [:service] response cursor))}))

(defn- log-handler
  [service-id]
  (handler/get
    (routes/path-for-backend :service-logs {:id service-id})
    {:on-call    (state/update-value [:fetching] true cursor)
     :on-success (fn [response]
                   (state/update-value [:initialized] true cursor)
                   (state/update-value [:fetching] false cursor)
                   (state/update-value [:data] response cursor))
     :on-error   #(state/update-value [:error] true cursor)}))

(defn- log-append-handler
  [service-id from-timestamp]
  (handler/get
    (routes/path-for-backend :service-logs {:id service-id})
    {:on-call    (state/update-value [:fetching] true cursor)
     :params     {:from from-timestamp}
     :on-success (fn [response]
                   (state/update-value [:fetching] false cursor)
                   (state/update-value [:data] (-> (state/get-value cursor)
                                                   :data
                                                   (concat response)) cursor))}))

(defn- init-state
  []
  (state/set-value {:filter      {:predicate ""}
                    :initialized false
                    :fetching    false
                    :autoscroll  false
                    :error       false
                    :timestamp   false
                    :data        []} cursor))

(def mixin-refresh-form
  (mixin/refresh-form
    (fn [{{:keys [id]} :params}]
      (when (not (:fetching (state/get-value cursor)))
        (log-append-handler id (-> (state/get-value cursor)
                                   :data
                                   (last)
                                   :timestamp))))))

(def mixin-init-form
  (mixin/init-form
    (fn [{{:keys [id]} :params}]
      (init-state)
      (service-handler id)
      (log-handler id))))

(rum/defc line < rum/static [item timestamp]
  [:div
   (when timestamp
     [:span.log-timestamp (:timestamp item)])
   [:span.log-info (str (:taskName item) "." (subs (:task item) 0 12) "@" (:taskNode item))]
   [:span.log-body (str " " (:line item))]])

(rum/defc form < rum/reactive
                 mixin-init-form
                 mixin-refresh-form
                 {:did-mount  (fn [state] (auto-scroll!) state)
                  :did-update (fn [state] (auto-scroll!) state)} [{{:keys [id]} :params}]
  (let [{:keys [filter data autoscroll timestamp initialized error service]} (state/react cursor)
        filtered-items (filter-items data (:predicate filter))]
    [:div
     [:div.form-panel
      [:div.form-panel-left
       (panel/info icon/services
                   (:serviceName service))]
      [:div.form-panel-right
       (comp/mui
         (comp/raised-button
           {:href  (routes/path-for-frontend :service-info {:id id})
            :label "Back"}))]]
     [:div.log-panel
      [:div.form-panel-left
       (panel/text-field
         {:hintText "Search in log"
          :onChange (fn [_ v]
                      (state/update-value [:filter :predicate] v cursor))})
       [:span.form-panel-space]
       (panel/checkbox
         {:checked timestamp
          :label   "Show timestamp"
          :onCheck (fn [_ v]
                     (state/update-value [:timestamp] v cursor))})]
      [:div.form-panel-right
       (panel/checkbox
         {:checked autoscroll
          :label   "Auto-scroll logs"
          :onCheck (fn [_ v]
                     (state/update-value [:autoscroll] v cursor))})]]
     [:div.log#service-log
      (cond
        error [:span "Logs for this service couldn't be fetched."]
        (and (empty? filtered-items) initialized) [:span "Log is empty in this service."]
        (not initialized) [:span "Loading..."]
        :else (map
                (fn [item]
                  (line item timestamp)) filtered-items))]]))
