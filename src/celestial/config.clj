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

(ns celestial.config
  "Celetial configuration info"
  (:use 
    [clojure.pprint :only (pprint)]
    [bouncer [core :as b] [validators :as v]]
    [bouncer.validators :only (defvalidator)]
    [taoensso.timbre :only (debug info error warn trace)]
    [clojure.core.strint :only (<<)]
    [clojure.java.io :only (file)]
    [clj-config.core :as conf])
  )

(def levels #{:trace :debug :info :error})

(defvalidator hash-v
  {:default-message-format "%s must be a hash"}
  [c] (map? c))

(defvalidator str-v
  {:default-message-format "%s must be a hash"}
  [c] (string? c))

(defmacro validate-nest 
  "Bouncer nested maps validation with prefix key"
  [target pref & body]
  (let [with-prefix (reduce (fn [r [ks vs]] (cons (into [pref] ks) (cons vs  r))) '() (partition 2 body))]
  `(b/validate ~target ~@with-prefix)))

(defn base-v [c]
  (b/validate c 
    [:redis :host] [v/required str-v]
    [:ssh :private-key-path] [v/required str-v]))

(defn celestial-v
  "Base config validation"
  [c]
  (validate-nest c :celestial
    [:port] [v/required v/number]
    [:https-port] [v/required v/number]
    [:log :level] [v/required (v/member levels :message (<< "log level must be either ~{levels}"))]
    [:log :path] [v/required str-v]
    [:cert :password] [v/required str-v]
    [:cert :keystore] [v/required str-v] ))

(defn proxmox-v 
  "proxmox section validation"
  [c]
  (validate-nest c [:hypervizor :proxmox]
    [:username] [v/required str-v]
    [:password] [v/required str-v]
    [:host] [v/required str-v]
    [:ssh-port] [v/required str-v]))

(defn aws-v 
  "proxmox section validation"
  [c]
  (validate-nest c [:hypervizor :aws]
    [:access-key] [v/required str-v]
    [:secret-key] [v/required str-v]
    [:endpoint] [v/required str-v]))

(defn validate-conf 
  "applies all validations on a configration map"
  [c]
  (cond-> (-> c celestial-v second base-v second)
    (get-in c [:hypervizor :proxmox]) ((comp second proxmox-v)) 
    (get-in c [:hypervizor :aws]) ((comp second aws-v)) 
    ))

(def config-paths
  ["/etc/celestial.edn" (<< "~(System/getProperty \"user.home\")/.celestial.edn")])

(def path 
  (first (filter #(.exists (file %)) config-paths)))

(defn pretty-error 
  "A pretty print error log"
  [m]
  (let [st (java.io.StringWriter.)]
    (binding [*out* st] 
      (clojure.pprint/pprint m))
    (error st))) 

(defn read-and-validate []
  (let [c (conf/read-config path) ]
    (when-let [v (:bouncer.core/errors (validate-conf c))] 
      (pretty-error v)
      (System/exit 1))
    c))


(def ^{:doc "main configuation"} config 
  (if path
    (read-and-validate)      
    (do 
      (error 
        (<< "Missing configuration file, you should configure celestial in either ~{config-paths}")) 
      (System/exit 1))))
 
