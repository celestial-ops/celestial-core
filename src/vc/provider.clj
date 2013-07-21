(comment 
   Celestial, Copyright 2012 Ronen Narkis, narkisr.com
   Licensed under the Apache License,
   Version 2.0  (the "License") you may not use this file except in compliance with the License.
   You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.)

(ns vc.provider 
  (:use 
    [celestial.provider :only (str? vec? mappings)]
    [trammel.core :only  (defconstrainedrecord)]
    [clojure.core.strint :only (<<)]
    [vc.vijava :only (clone power-on power-off status destroy guest-status)]
    [vc.guest :only (set-ip)]
    [vc.validations :only (provider-validation)]
    [celestial.core :only (Vm)]
    [celestial.common :only (import-logging)]
    [slingshot.slingshot :only  [throw+ try+]]
    [celestial.provider :only (str? vec? mappings transform os->template wait-for)]
    [celestial.model :only (translate vconstruct)])
  )

(import-logging)

(defn wait-for-guest
  "waiting for guest to boot up"
  [hostname timeout]
  (wait-for {:timeout timeout} #(= :running (guest-status hostname))
    {:type ::vc:guest-failed :message "Timed out on waiting for guest to start" :hostname hostname}))


(defconstrainedrecord VirtualMachine [hostname allocation machine]
  "A vCenter Virtual machine instance"
  [(not (nil? hostname)) (provider-validation allocation machine)]
  Vm
  (create [this] 
    (clone hostname allocation machine)
    (when (machine :ip)
      (.start this)  
      (set-ip hostname (select-keys machine [:user :password :sudo]) machine)))

  (delete [this] (destroy hostname))

  (start [this] 
      (when-not (= (.status this) "running") 
        (power-on hostname) 
        (wait-for-guest hostname [10 :minute])))

  (stop [this] (power-off hostname))

  (status [this] 
     (try+ (status hostname) 
       (catch [:type :vc.vijava/missing-entity] e
         (warn "No VM found, most chances it hasn't been created yet") false))))

(def machine-ks [:template :cpus :memory :ip :mask :network :gateway :search :names :user :password :sudo])

(def allocation-ks [:pool :datacenter :disk-format :hostsystem])

(defn select-from [ks] (fn[m] (select-keys m ks)))

(def selections (juxt :hostname (select-from allocation-ks) (select-from machine-ks)))

(defmethod translate :vcenter [{:keys [machine vcenter system-id]}]
  "Convert the general model into a vc specific one"
  (-> (merge machine vcenter {:system-id system-id})
      (mappings {:os :template})
      (transform {:template (os->template :vcenter) :disk-format keyword})
      selections
      ))

(defmethod vconstruct :vcenter [spec]
  (let [[hostname allocation machine] (translate spec)]
    (->VirtualMachine hostname allocation machine)))

