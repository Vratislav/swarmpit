(ns swarmpit.docker.mapper.outbound
  "Map swarmpit domain to docker domain"
  (:require [clojure.string :as str]
            [swarmpit.base64 :as base64]))

(defn- as-bytes
  [megabytes]
  (* megabytes (* 1024 1024)))

(defn ->auth-config
  "Pass registry or dockeruser entity"
  [auth-entity]
  {:username      (:username auth-entity)
   :password      (:password auth-entity)
   :serveraddress (:url auth-entity)})

(defn ->service-mode
  [service]
  (if (= (:mode service) "global")
    {:Global {}}
    {:Replicated
     {:Replicas (:replicas service)}}))

(defn ->service-ports
  [service]
  (->> (:ports service)
       (filter #(and (> (:hostPort %) 0)
                     (> (:containerPort %) 0)))
       (map (fn [p] {:Protocol      (:protocol p)
                     :PublishedPort (:hostPort p)
                     :TargetPort    (:containerPort p)}))
       (into [])))

(defn- ->service-networks
  [service]
  (->> (:networks service)
       (map #(hash-map :Target (:networkName %)
                       :Aliases (:serviceAliases %)))
       (into [])))

(defn ->service-variables
  [service]
  (->> (:variables service)
       (filter #(not (and (str/blank? (:name %))
                          (str/blank? (:value %)))))
       (map (fn [p] (str (:name p) "=" (:value p))))
       (into [])))

(defn ->service-labels
  [service]
  (->> (:labels service)
       (filter #(not (and (str/blank? (:name %))
                          (str/blank? (:value %)))))
       (map (fn [l] {(:name l) (:value l)}))
       (into {})))

(defn ->service-log-options
  [service]
  (->> (get-in service [:logdriver :opts])
       (filter #(not (and (str/blank? (:name %))
                          (str/blank? (:value %)))))
       (map (fn [l] {(:name l) (:value l)}))
       (into {})))

(defn ->service-volume-options
  [service-volume]
  (when (some? service-volume)
    {:Labels       (:labels service-volume)
     :DriverConfig {:Name    (get-in service-volume [:driver :name])
                    :Options (get-in service-volume [:driver :options])}}))

(defn ->service-mounts
  [service]
  (->> (:mounts service)
       (map (fn [v] {:ReadOnly      (:readOnly v)
                     :Source        (:host v)
                     :Target        (:containerPath v)
                     :Type          (:type v)
                     :VolumeOptions (->service-volume-options (:volumeOptions v))}))
       (into [])))

(defn- ->service-placement-contraints
  [service]
  (->> (get-in service [:deployment :placement])
       (map (fn [p] (:rule p)))
       (into [])))

(defn- ->secret-id
  [secret-name secrets]
  (->> secrets
       (filter #(= secret-name (:secretName %)))
       (first)
       :id))

(defn ->secret-target
  [secret]
  (let [secret-target (:secretTarget secret)]
    (if (str/blank? secret-target)
      (:secretName secret)
      secret-target)))

(defn ->service-secrets
  [service secrets]
  (->> (:secrets service)
       (map (fn [s] {:SecretName (:secretName s)
                     :SecretID   (->secret-id (:secretName s) secrets)
                     :File       {:GID  "0"
                                  :Mode 292
                                  :Name (->secret-target s)
                                  :UID  "0"}}))
       (into [])))

(defn ->service-resource
  [service-resource]
  (let [cpu (:cpu service-resource)
        memory (:memory service-resource)]
    {:NanoCPUs    (when (some? cpu)
                    (-> cpu
                        (* 1000000000)
                        (long)))
     :MemoryBytes (when (some? memory)
                    (as-bytes memory))}))

(defn ->service-update-config
  [service]
  (let [update (get-in service [:deployment :update])]
    {:Parallelism   (:parallelism update)
     :Delay         (* (:delay update) 1000000000)
     :FailureAction (:failureAction update)}))

(defn ->service-rollback-config
  [service]
  (let [rollback (get-in service [:deployment :rollback])]
    {:Parallelism   (:parallelism rollback)
     :Delay         (* (:delay rollback) 1000000000)
     :FailureAction (:failureAction rollback)}))

(defn ->service-restart-policy
  [service]
  (let [policy (get-in service [:deployment :restartPolicy])]
    {:Condition   (:condition policy)
     :Delay       (* (:delay policy) 1000000000)
     :MaxAttempts (:attempts policy)}))

(defn ->service-image-registry
  [service registry]
  (let [repository (get-in service [:repository :name])
        tag (get-in service [:repository :tag])
        url (second (str/split (:url registry) #"//"))]
    (str url "/" repository ":" tag)))

(defn ->service-image
  [service]
  (let [repository (get-in service [:repository :name])
        tag (get-in service [:repository :tag])]
    (str repository ":" tag)))

(defn ->service-metadata
  [service image]
  (let [autoredeploy (get-in service [:deployment :autoredeploy])
        stack (:stack service)
        image-id (get-in service [:repository :imageId])
        distribution-id (get-in service [:distribution :id])
        distribution-type (get-in service [:distribution :type])]
    (merge {}
           (when (some? stack)
             {:com.docker.stack.namespace stack
              :com.docker.stack.image     image})
           (when (some? autoredeploy)
             {:swarmpit.service.deployment.autoredeploy (str autoredeploy)})
           (when (some? image-id)
             {:swarmpit.service.repository.image.id image-id})
           (when (some? distribution-type)
             {:swarmpit.service.distribution.id   distribution-id
              :swarmpit.service.distribution.type distribution-type}))))

(defn ->service-container-metadata
  [service]
  (let [stack (:stack service)]
    (merge {}
           (when (some? stack)
             {:com.docker.stack.namespace stack}))))

(defn ->service
  [service image]
  {:Name           (:serviceName service)
   :Labels         (merge
                     (->service-labels service)
                     (->service-metadata service image))
   :TaskTemplate   {:ContainerSpec {:Image   image
                                    :Labels  (->service-container-metadata service)
                                    :Mounts  (->service-mounts service)
                                    :Secrets (:secrets service)
                                    :Env     (->service-variables service)}
                    :LogDriver     {:Name    (get-in service [:logdriver :name])
                                    :Options (->service-log-options service)}
                    :Resources     {:Limits       (->service-resource (get-in service [:resources :limit]))
                                    :Reservations (->service-resource (get-in service [:resources :reservation]))}
                    :RestartPolicy (->service-restart-policy service)
                    :Placement     {:Constraints (->service-placement-contraints service)}
                    :ForceUpdate   (get-in service [:deployment :forceUpdate])
                    :Networks      (->service-networks service)}
   :Mode           (->service-mode service)
   :UpdateConfig   (->service-update-config service)
   :RollbackConfig (->service-rollback-config service)
   :EndpointSpec   {:Ports (->service-ports service)}})

(defn ->network-ipam
  [network]
  (let [ipam (:ipam network)
        gateway (:gateway ipam)
        subnet (:subnet ipam)]
    (if (and (not (str/blank? gateway))
             (not (str/blank? subnet)))
      {:Config [{:Subnet  subnet
                 :Gateway gateway}]})))

(defn ->network
  [network]
  {:Name     (:networkName network)
   :Driver   (:driver network)
   :Internal (:internal network)
   :IPAM     (->network-ipam network)})

(defn ->volume
  [volume]
  {:Name    (:volumeName volume)
   :Driver  (:driver volume)
   :Options (:options volume)
   :Labels  (:labels volume)})

(defn ->secret
  [secret]
  {:Name (:secretName secret)
   :Data (if (:encode secret)
           (base64/encode (:data secret))
           (:data secret))})