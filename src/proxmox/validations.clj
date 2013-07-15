(ns proxmox.validations
  "Proxmox based validations"
  (:use 
    [clojure.core.strint :only (<<)]
    [bouncer [core :as b] [validators :as v :only (defvalidatorset)]])
  (:require 
    [celestial.validations :as cv]))

(defvalidatorset machine-common
    :cpus [v/number v/required]
    :disk [v/number v/required]
    :memory [v/number v/required]
    :hostname [v/required cv/str?]
  )

(def prox-types [:ct :vm])

(defn greater-then [from i] (> i from))

(defvalidatorset proxmox-entity
    :type [v/required 
           (v/member prox-types  :message (<< "Proxmox VM type must be either ~{prox-types}" ))]
    :vmid [(v/custom (partial greater-then 100) :message "vmid must be greater then 100"
                     :pre #(-> % :proxmox :vmid nil? not))]
    :password [v/required cv/str?]
    :nameserver [cv/str?])

(defvalidatorset machine-entity
     :domain [v/required cv/str?]
     :ip [cv/str?]
     :os [v/required cv/keyword?])

(defvalidatorset entity-validation
   :machine machine-common 
   :machine machine-entity
   :proxmox proxmox-entity)

(defn validate-entity
 "proxmox based system entity validation for persistence layer" 
  [proxmox]
   (cv/valid! ::invalid-system proxmox entity-validation))

(defvalidatorset extended
    :id [v/number]          
    :features [cv/sequential?])

(defvalidatorset machine-provider
     :vmid [v/required v/number ]
     :password [v/required cv/str?]
     :nameserver [cv/str?] 
     :hostname [(v/custom (partial re-find #".*\.\w*") :message "hostname must be fully qualified")]
     :ip_address [cv/str?]
     :ostemplate [v/required cv/str?] )

(defvalidatorset provider-validation
   :machine machine-common 
   :machine machine-provider
   :extended extended
  )

(defn provider-validation
  "Almost the same validation as persisted with small mapped properties modifications"
  [machine extended]
    (cv/valid! ::invalid-container {:machine machine :extended extended} provider-validation))

