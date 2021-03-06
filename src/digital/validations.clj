(comment 
  re-core, Copyright 2012 Ronen Narkis, narkisr.com
  Licensed under the Apache License,
  Version 2.0  (the "License") you may not use this file except in compliance with the License.
  You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.)

(ns digital.validations
  "Digital ocean validations"
  (:require 
    [re-core.model :refer (check-validity)]
    [clojure.core.strint :refer (<<)]
    [subs.core :as subs :refer (validate! combine every-v every-kv validation when-not-nil)]))

(def machine-entity
  {:machine {
     :hostname #{:required :String} :domain #{:required :String} 
     :user #{:required :String} :os #{:required :Keyword} 
  }})

(def digital-entity
  {:digital-ocean
     {:region #{:required :String} :size #{:required :String}
      :backups #{:Boolean} :private-networking #{:Boolean}
      }
    })

(defmethod check-validity [:digital-ocean :entity] [droplet]
  (validate! droplet (combine machine-entity digital-entity) :error ::invalid-system))
 
(validation :ssh-key* (every-v #{:String}))

(def digital-provider
  {:region #{:required :String} :size #{:required :String}
   :backups #{:Boolean} :private-networking #{:Boolean}
   :ssh-keys #{:Vector :ssh-key*} :image #{:required :String}
  })

(defn provider-validation [droplet]
  (validate! droplet digital-provider :error ::invalid-droplet))
