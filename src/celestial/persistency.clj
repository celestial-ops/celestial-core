(ns celestial.persistency
  (:refer-clojure :exclude [type])
  (:use 
    [clojure.string :only (split join)]
    [celestial.redis :only (wcar)]
    [slingshot.slingshot :only  [throw+ try+]]
    [clojure.core.strint :only (<<)]) 
  (:require 
    [taoensso.carmine :as car]))


(defn tk [id] (<< "type:~{id}"))
(defn hk [id] (<< "host:~{id}"))

(defn type [t]
  "Reading a type"
  (if-let [res (wcar (car/get (tk t)))] res 
    (throw+ {:type ::missing-type :t t})))

(defn new-type [t spec]
  "An application type and its spec see fixtures/redis-type.edn"
  (wcar (car/set (tk t) spec)))

(defn register-host [host t m]
  {:pre [(type t)]}
  "Mapping host to a given type and its machine"
  (wcar 
    (car/hset (hk host) :machine m)
    (car/hset (hk host) :type t)))

(defn hgetall [h]
  "require since expect fails to mock otherwise"
  (wcar (car/hgetall (hk h))))

(defn host [h]
  (if-let [data (hgetall h)]
    (apply merge (map (partial apply hash-map) (partition 2 data)))
    (throw+ {:type ::missing-host :host h})))

(defn fuzzy-host [h]
  "Searches after a host in a fuzzy manner, first fqn then tried prefixes"
  (let [ks (reverse (reductions (fn [r v] (str r "." v)) (split h #"\.")))]
    (println ks)
    (when-let [k (first (filter #(= 1 (wcar (car/exists (hk %)))) ks))]
      (host k))))

(comment 
  (new-type "z" {}) 
  (register-host "foo" "z" {:foo 1}) 
  (host "foo")) 


