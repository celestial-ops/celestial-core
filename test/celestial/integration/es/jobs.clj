(ns celestial.integration.es.jobs
  "Done jobs persistency"
 (:require 
   [es.jobs :as jobs]
   [es.common :as es]
   [clojurewerkz.elastisch.query :as q]
   [celestial.security :refer (set-user)]
   [celestial.fixtures.data :refer (redis-prox-spec redis-ec2-spec)]
   [celestial.fixtures.core :refer (with-conf)]
   [celestial.fixtures.populate :refer (add-users re-initlize)])
  (:use midje.sweet))
 

(def job {:tid "" :status :success :identity 1 :args [] :env :dev })

(defn stamp 
   "add time start end timestamps" 
   [m]
  (merge m {:start (System/currentTimeMillis) :end (+ 1000 (System/currentTimeMillis))}))

(defn add-jobs
   "adds a list of systems into ES" 
   []
  (es/initialize)
  (jobs/put (-> job (merge {:tid "1" :status :success}) stamp) 2000)        
  (jobs/put (-> job (merge {:tid "2" :status :error :env :prod}) stamp) 2000)        
  (jobs/put (-> job (merge {:tid "3" :status :success :identity 2}) stamp) 2000)        
  (jobs/put (-> job (merge {:tid "4" :status :error}) stamp) 2000 :flush? true))

(defn total [m]
  (-> m :hits :hits count))

(with-conf
  (against-background [(before :facts (do (re-initlize true) (add-jobs)))]
   (fact "basic job get" :integration :elasticsearch
     (get-in (jobs/get "1") [:source :status]) => ":success"
     (get-in (jobs/get "2") [:source :env]) => ":prod"
     (get-in (jobs/get "3") [:source :identity]) => 2)

   (fact "jobs pagination" :integration :elasticsearch
     (total (jobs/paginate 0 5 ["dev"])) => 3
     (total (jobs/paginate 0 5 ["prod"]))=> 1
     (total (jobs/paginate 0 5 ["prod" "dev"])) => 4)))