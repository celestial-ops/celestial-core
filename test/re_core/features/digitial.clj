(ns re-core.features.digitial
  "digital ocean support"
  (:require 
    [re-core.fixtures.core :refer (with-defaults is-type? with-admin with-conf)]  
    [re-core.persistency.systems :as s]
    [re-core.fixtures.data :refer (redis-type local-conf redis-freenas)]  
    [re-core.fixtures.populate :refer (populate-system)]  
    [re-core.integration.workflows.common :refer (spec get-spec)]
    [re-core.workflows :as wf] 
    [re-core.model :refer (vconstruct)]
    [re-core.fixtures.core :refer (with-conf) :as f]
    [re-core.fixtures.data :refer [redis-digital]])
  (:use midje.sweet)
  (:import clojure.lang.ExceptionInfo)
 )

(with-conf
  (let [{:keys [machine digital-ocean]} redis-digital]
    (fact "legal digital-ocean system" :digital-ocean
       (:drp (vconstruct redis-digital)) => (contains {:name "red1.local"}))))

(with-admin
  (with-conf local-conf
    (with-state-changes [(before :facts (populate-system redis-type redis-digital))]
      (fact "digital-ocean creation workflows" :integration :digital-ocean :workflow
          (wf/create (spec)) => nil 
          (wf/stop (spec)) => nil 
          (wf/start (spec)) => nil 
          (wf/destroy (spec)) => nil
          ))))
