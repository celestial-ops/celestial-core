(ns re-core.fixtures.populate
  "data population"
  (:gen-class true)
  (:require
    [re-core.persistency [users :as u] [types :as t]]
    [es.common :as es]
    [re-core.model :refer (figure-virt)]
    [re-core.security :refer (set-user)]
    [re-core.fixtures.core :refer (with-conf)]
    [clojure.test.check.generators :as g]
    [re-core.redis :as red]
    [re-core.persistency.core :as c]
    [re-core.persistency.systems :as s]
    [re-core.persistency.actions :as a]
    [re-core.fixtures.data :refer (admin ronen) :as d]))

(defn add-users
  "populates admin and ronen users"
  []
  (u/add-user admin)
  (u/add-user ronen))

(defn add-types
   "populates types"
   []
  (t/add-type d/smokeping-type)
  (t/add-type d/jvm-type)
  (t/add-type d/redis-type))

(defn add-actions
   "populates actions"
   []
  (a/add-action d/redis-deploy)
  ; these actions won't work
  (a/add-action (assoc d/redis-deploy :name "restart-tomcat"))
  (a/add-action (assoc d/redis-deploy :name "flush-cache")))



(def host
  (g/fmap (partial apply str)
    (g/tuple (g/elements ["zeus-" "atlas-" "romulus-" "remus-"]) g/nat)))

(def ip (g/fmap #(str "192.168.1." %) (g/such-that #(<= (.length %) 3) (g/fmap str g/nat))))

(def machines
  (g/fmap (partial zipmap [:hostname :ip]) (g/tuple host ip)))

(def host-env-gen
  (g/fmap (partial zipmap [:env :type])
      (g/tuple
         (g/elements [:dev :qa :prod])
         (g/elements ["redis" "smokeping"]))))

(defn with-ids [m]
  (let [virt (figure-virt m) ids {:openstack :instance-id :aws :instance-id}]
    (g/fmap #(merge-with merge m %)
      (g/hash-map virt
        (g/hash-map (ids virt) (g/such-that #(> (.length %) 10) g/string-alphanumeric 100))))))

(def systems-gen
  (g/bind host-env-gen
    (fn [v]
      (g/fmap #(merge % v)
        (g/one-of
          (into [(g/return d/redis-kvm-spec)] (mapv with-ids [d/redis-ec2-spec d/redis-openstack-spec])))))))

(def systems-with-machines
  (g/bind machines
    (fn [v]
      (g/fmap #(update-in % [:machine] (fn [m] (merge m v))) systems-gen))))

(defn add-systems []
  (doseq [s (g/sample systems-with-machines 100)]
    (s/add-system s)))

(defn add-templates
   "populate templates"
   []
  (s/add-template d/small-redis)
  (s/add-template d/tiny-jvm))

(defn re-initlize
  "Re-init datastores"
  ([] (re-initlize false))
  ([clear-es]
   (c/initilize-puny)
   (when clear-es (es/clear))
   (es/initialize)
   (red/clear-all)))

(def populators {
  :users add-users :types add-types :actions add-actions
  :systems add-systems :templates add-templates
})

(defn populate-all
  "populates all data types"
  [& {:keys [skip] :or {skip []}}]
  (re-initlize true)
  (doseq [[_ p] (dissoc populators skip)] (p)))

(defn populate-system
  "Adds single type and system"
  [t s]
  (re-initlize)
  (add-users)
  (t/add-type t)
  (s/add-system s))

(defn -main
  "run populate all"
  [& args]
  (set-user {:username "admin"}
     (populate-all)
     (es/flush-)
     (u/delete-user "admin"); will be set up in next launch
     (println "populate done!")))
