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

(ns aws.provider
  (:require 
    [amazonica.aws.ec2 :as ec2]
    [aws.common :refer (with-ctx instance-desc creds image-id)]
    [aws.networking :refer (update-ip set-hostname pub-dns)]
    [aws.volumes :refer (delete-volumes handle-volumes)]
    [aws.sdk.eip :as eip]
    [aws.validations :refer (provider-validation)]
    [celestial.persistency :as p]
    [clojure.core.strint :refer (<<)] 
    [supernal.sshj :refer (ssh-up?)] 
    [flatland.useful.utils :refer (defm)] 
    [flatland.useful.map :refer (dissoc-in*)] 
    [slingshot.slingshot :refer  [throw+ try+]] 
    [trammel.core :refer (defconstrainedrecord)] 
    [celestial.provider :refer (wait-for wait-for-ssh)] 
    [celestial.core :refer (Vm)] 
    [celestial.common :refer (import-logging )] 
    [celestial.persistency.systems :as s]
    [celestial.model :refer (translate vconstruct)]))

(import-logging)

(defn wait-for-status [instance req-stat timeout]
  "Waiting for ec2 machine status timeout is in mili"
  (wait-for {:timeout timeout} #(= req-stat (.status instance))
    {:type ::aws:status-failed :message "Timed out on waiting for status" :status req-stat :timeout timeout}))

(defn instance-id*
  "grabbing instance id of spec"
   [spec]
  (get-in (s/get-system (spec :system-id)) [:aws :instance-id]))

(defmacro with-instance-id [& body]
 `(if-let [~'instance-id (instance-id* ~'spec)]
    (do ~@body) 
    (throw+ {:type ::aws:missing-id :message "Instance id not found"}))) 

(defn creation-keys [aws]
  (clojure.set/subset? (into #{} (keys aws))
    #{:volumes :min-count :max-count :instance-type :key-name}))

(defn create-instance 
   "creates instance from aws" 
   [{:keys [aws machine] :as spec} endpoint]
   {:pre [(creation-keys aws)]}
   (let [inst (merge (dissoc aws :volumes) {:image-id (image-id machine)})]
     (get-in (with-ctx ec2/run-instances inst) [:instances 0 :instance-id])))

(defconstrainedrecord Instance [endpoint spec user]
  "An Ec2 instance"
  [(provider-validation spec) (-> endpoint nil? not)]
  Vm
  (create [this] 
    (let [instance-id (create-instance spec endpoint)]
       (s/partial-system (spec :system-id) {:aws {:instance-id instance-id}})
       (debug "created" instance-id)
       (handle-volumes spec endpoint instance-id)    
       (when-let [ip (get-in spec [:machine :ip])] 
         (debug (<<  "Associating existing ip ~{ip} to instance-id"))
          (with-ctx eip/assoc-pub-ip instance-id ip))
       (update-ip spec endpoint instance-id)
       (wait-for-ssh (pub-dns endpoint instance-id) user [5 :minute])
       (set-hostname spec endpoint instance-id user)
        this))

  (start [this]
    (with-instance-id
      (debug "starting" instance-id)
      (with-ctx ec2/start-instances {:instance-ids [instance-id]}) 
      (wait-for-status this "running" [5 :minute]) 
      (when-let [ip (get-in spec [:machine :ip])] 
        (debug (<<  "Associating existing ip ~{ip} to instance-id"))
        (with-ctx eip/assoc-pub-ip instance-id ip))
      (update-ip spec endpoint instance-id)
      (wait-for-ssh (pub-dns endpoint instance-id) user [5 :minute])
      ))

  (delete [this]
    (with-instance-id
      (debug "deleting" instance-id)
      (delete-volumes endpoint instance-id (spec :system-id)) 
      (with-ctx ec2/terminate-instances {:instance-ids instance-id}) 
      (wait-for-status this "terminated" [5 :minute])
      ; for reload support
      (s/update-system (spec :system-id) 
         (dissoc-in* (s/get-system (spec :system-id)) [:aws :instance-id]))
      ))

  (stop [this]
    (with-instance-id 
       (debug "stopping" instance-id)
       (when-not (first (:addresses (with-ctx eip/describe-eip instance-id)))
         (debug "clearing dynamic ip from system")
         (s/update-system (spec :system-id) 
           (dissoc-in* (s/get-system (spec :system-id)) [:machine :ip])))
       (with-ctx ec2/stop-instances {:instance-ids instance-id}) 
       (wait-for-status this "stopped" [5 :minute])))

  (status [this] 
    (try+ 
      (with-instance-id 
         (instance-desc endpoint instance-id :state :name)) 
      (catch [:type ::aws:missing-id] e 
        (debug "No AWS instance id, most chances this instance hasn't been created yet") false))))

(def defaults {:aws {:min-count 1 :max-count 1}})

(defn aws-spec 
  "creates an ec2 spec" 
  [{:keys [aws machine] :as spec}]
  (merge-with merge (dissoc-in* spec [:aws :endpoint]) defaults))

(defmethod translate :aws [{:keys [aws machine] :as spec}] 
  [(aws :endpoint) (aws-spec spec) (or (machine :user) "root")])

(defmethod vconstruct :aws [spec]
  (apply ->Instance (translate spec)))

(comment 
  (use 'celestial.fixtures.data 'celestial.fixtures.core )
  (def m (vconstruct redis-ec2-spec)) 
  (with-admin (with-conf local-conf (.status m)))
  (with-conf local-conf (with-admin (.create m)))
  (.start m)
  (clojure.pprint/pprint 
    (celestial.model/set-env :dev 
     (first (:addresses (eip/describe-eip (assoc (creds) :endpoint "ec2.eu-west-1.amazonaws.com") "i-cdd59781"))))) 
  ) 


