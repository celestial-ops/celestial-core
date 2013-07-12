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
  (:require [aws.sdk.ec2 :as ec2])
  (:import (java.util UUID))
  (:require [celestial.persistency :as p]
            [celestial.validations :as cv])
  (:use 
    [clojure.string :only (join)]
    [aws.validations :only (provider-validation)]
    [bouncer [core :as b] [validators :as v]]
    [clojure.core.strint :only (<<)]
    [supernal.sshj :only (execute ssh-up?)]
    [flatland.useful.utils :only (defm)]
    [flatland.useful.map :only (dissoc-in*)]
    [slingshot.slingshot :only  [throw+ try+]]
    [aws.sdk.ec2 :only 
     (run-instances describe-instances terminate-instances start-instances
                     create-tags stop-instances instance-filter instance-id-filter)]
    [trammel.core :only (defconstrainedrecord)]
    [celestial.provider :only (str? vec? wait-for)]
    [celestial.redis :only (synched-map)]
    [celestial.core :only (Vm)]
    [celestial.common :only (get! import-logging )]
    [celestial.model :only (translate vconstruct)]))

(import-logging)

(defn creds [] (get! :hypervisor :aws))

(defn wait-for-status [instance req-stat timeout]
  "Waiting for ec2 machine status timeout is in mili"
  (wait-for {:timeout timeout} #(= req-stat (.status instance))
    {:type ::aws:status-failed :message "Timed out on waiting for status" :status req-stat :timeout timeout}))

(defmacro ec2 [f & args]
  `(~f (assoc (creds) :endpoint ~'endpoint) ~@args))

(defn image-desc [endpoint ami & ks]
  (-> (ec2/describe-images (assoc (creds) :endpoint endpoint) (ec2/image-id-filter ami))
      first (apply ks)))

(defn instance-desc [endpoint instance-id & ks]
  (-> (ec2 describe-instances (instance-id-filter instance-id))
      first :instances first (get-in ks)))

(defn wait-for-attach [endpoint instance-id timeout]
  (wait-for {:timeout timeout} 
            #(= "attached" (instance-desc endpoint instance-id :block-device-mappings 0 :ebs :status)) 
            {:type ::aws:ebs-attach-failed :message "Failed to wait for ebs root device attach"}))

(defn pub-dns [endpoint instance-id]
  (instance-desc endpoint instance-id :public-dns))

(defn wait-for-ssh [endpoint instance-id user timeout]
    (wait-for {:timeout timeout}
              #(ssh-up? {:host (pub-dns endpoint instance-id) :port 22 :user user})
              {:type ::aws:ssh-failed :message "Timed out while waiting for ssh" :timeout timeout}))

(defn pubdns-to-ip
  "Grabs public ip from dnsname ec2-54-216-121-122.eu-west-1.compute.amazonaws.com"
   [pubdns]
    (join "." (rest (re-find #"ec2\-(\d+)-(\d+)-(\d+)-(\d+).*" pubdns))))

(defn update-pubdns [spec endpoint instance-id]
  "updates public dns in the machine persisted data"
  (when (p/system-exists? (spec :system-id))
    (let [ec2-host (pub-dns endpoint instance-id)]
      (p/partial-system (spec :system-id) {:machine {:ssh-host ec2-host :ip (pubdns-to-ip ec2-host)}}))))

(defn set-hostname [spec endpoint instance-id user]
  "Uses a generic method of setting hostname in Linux"
  (let [hostname (get-in spec [:machine :hostname]) remote {:host (pub-dns endpoint instance-id) :user user}]
    (execute (<< "echo kernel.hostname=~{hostname} | sudo tee -a /etc/sysctl.conf") remote )
    (execute "sudo sysctl -p" remote) 
    (ec2 create-tags [(instance-desc endpoint instance-id :id)] {:Name hostname})
    ))

(defn instance-id*
  "grabbing instance id of spec"
   [spec]
  (get-in (p/get-system (spec :system-id)) [:aws :instance-id]))


(defmacro with-instance-id [& body]
 `(if-let [~'instance-id (instance-id* ~'spec)]
    (do ~@body) 
    (throw+ {:type ::aws:missing-id :message "Instance id not found"}))) 

(defconstrainedrecord Instance [endpoint spec instance-id user]
  "An Ec2 instance"
  [(provider-validation spec) (-> endpoint nil? not)]
  Vm
  (create [this] 
        (let [{:keys [aws]} spec instance-id (-> (ec2 run-instances aws) :instances first :id)]
          (p/partial-system (spec :system-id) {:aws {:instance-id instance-id}})
          (debug "created" instance-id)
          (when (= (image-desc endpoint (aws :image-id) :root-device-type) "ebs")
            (wait-for-attach endpoint instance-id [10 :minute])) 
          (update-pubdns spec endpoint instance-id)
          (wait-for-ssh endpoint instance-id user [5 :minute])
          (set-hostname spec endpoint instance-id user)
          this))
  (start [this]
         (with-instance-id
           (debug "starting" instance-id)
           (ec2 start-instances instance-id) 
           (wait-for-status this "running" [5 :minute]) 
           (update-pubdns spec endpoint instance-id)))
  (delete [this]
        (with-instance-id
           (debug "deleting" instance-id)
           (ec2 terminate-instances instance-id ) 
           (wait-for-status this "terminated" [5 :minute])))
  (stop [this]
        (with-instance-id 
          (debug "stopping" instance-id)
          (ec2 stop-instances instance-id) 
          (wait-for-status this "stopped" [5 :minute])))
  (status [this] 
        (with-instance-id
          (instance-desc endpoint instance-id :state :name))))

(def defaults {:aws {:min-count 1 :max-count 1}})

(defn aws-spec 
  "creates an ec2 spec" 
  [{:keys [aws machine] :as spec}]
  (merge-with merge (dissoc-in* spec [:aws :endpoint]) defaults))

(defmethod translate :aws [{:keys [aws machine] :as spec}] 
  [(aws :endpoint) (aws-spec spec)  (str (UUID/randomUUID)) (or (machine :user) "root")])

(defmethod vconstruct :aws [spec]
  (apply ->Instance (translate spec)))

(comment 
  (use 'celestial.fixtures)
  (def m (.create (vconstruct celestial.fixtures/puppet-ami))) 
  (.status m)
  (.start m)
  (instance-desc "ec2.eu-west-1.amazonaws.com" "3e28fbd5-bce9-4580-9095-05e982fdd7bd" :block-device-mappings 0 :ebs :status)
  (-> (ec2/describe-images (assoc (creds) :endpoint "ec2.eu-west-1.amazonaws.com") (ec2/image-id-filter "ami-64636a10"))
      first :root-device-type  
      )

  (-> (ec2/describe-images (creds) (ec2/image-id-filter "ami-5a60692e"))
      first :root-device-type  
      )
  ) 

