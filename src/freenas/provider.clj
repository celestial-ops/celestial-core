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

(ns freenas.provider
  "Freenas jails provider"
  (:require 
    [taoensso.timbre :as timbre]
    [clojure.core.strint :refer (<<)] 
    [celestial.provider :refer (wait-for-ssh mappings wait-for)]
    [freenas.remote :refer [call]]
    [freenas.validations :refer [validate-provider jail-mappings]]
    [slingshot.slingshot :refer  [throw+]]
    [celestial.persistency.systems :as s]
    [celestial.common :refer (import-logging)]
    [org.httpkit.client :as client]
    [celestial.core :refer (Vm)] 
    [celestial.model :refer (hypervisor translate vconstruct)])
 )

(timbre/refer-timbre)

(defn instance-id*
  "grabbing instance id of spec"
   [spec]
  (get-in (s/get-system (spec :system-id)) [:freenas :id]))

(defmacro with-id [& body]
 `(if-let [~'id (instance-id* ~'spec)]
    (do ~@body) 
    (throw+ {:type ::freenas:missing-id} "Instance id not found"))
  )

(defn jails [host] 
  (let [js (call client/get "jails/jails/") ]
    (first (filter #(= (:jail_host %) host) js))))

(defn enable-ssh [host]
  ;; jexec 19 sed -i.bak 's/^sshd_enable=.*/sshd_enable="YES"/' /etc/rc.conf
  ;; jexec  19 /etc/rc.d/sshd start
  )

(defrecord Jail [spec]
  Vm
  (create [this] 
    (let [{:keys [jail_host]} (call client/post "jails/jails/" spec)
          {:keys [id]} (jails jail_host)]
     (debug "created" id)
     (s/partial-system (spec :system-id) {:freenas {:id id}})
     (enable-ssh jail_host)
      this))

  (start [this]
    (with-id
      (let [{:keys [machine]} (s/get-system (spec :system-id))]
        (when-not (= "running" (.status this))
          (call client/post (<< "jails/jails/~{id}/start") spec))
        (wait-for-ssh (machine :ip) "root" [5 :minute]))
      ))

  (delete [this]
     (with-id (call client/delete (<< "jails/jails/~{id}/"))))

  (stop [this]
    (with-id (call client/post (<< "jails/jails/~{id}/stop/"))))

  (status [this] 
    (when-let [jail (jails (spec :jail_host))]
      (.toLowerCase (:jail_status jail)))))


(defmethod translate :freenas [{:keys [machine freenas system-id] :as spec}]
    "Convert the general model into a freenas jail"
    (mappings (merge machine freenas {:system-id system-id}) jail-mappings))

(defn validate [spec] 
  (validate-provider spec) spec)

(defmethod vconstruct :freenas [spec]
  (Jail. (validate (translate spec))))
