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

(ns celestial.persistency.core
  (:require
    [celestial.persistency.users :as u]
    [celestial.common :refer (import-logging)]
    [celestial.redis :refer (server-conn)]
    [celestial.persistency.migrations :as mg]
    [components.core :refer (Lifecyle)] 
    [puny.redis :as r]))

(import-logging)

(defn initilize-puny 
   "Initlizes puny connection" 
   []
  (info "Initializing puny connection" (server-conn))
  (r/server-conn (server-conn)))

(defrecord Persistency
  []
  Lifecyle
  (setup [this]
    (u/reset-admin)
    (mg/setup-migrations)) 
  (start [this] 
    (initilize-puny)
    )
  (stop [this])
  )

(defn instance 
   "creats a jobs instance" 
   []
  (Persistency.))
