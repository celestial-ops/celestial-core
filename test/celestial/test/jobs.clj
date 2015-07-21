(ns celestial.test.jobs
  (:require 
    [celestial.persistency.systems :as s]
    [taoensso.carmine :as car]
    [celestial.redis :refer (server-conn)]
    [taoensso.carmine.locks :refer (with-lock acquire-lock)]
    [celestial.common :refer (minute)]
    [clojure.core.strint :refer (<<)]
    [celestial.jobs.systems :refer (job-exec)]
    [celestial.jobs.common :refer (save-status)]
    [celestial.jobs.core :refer (enqueue) :as jobs])
  (:use midje.sweet)
  (:import java.lang.AssertionError))


(fact "with-lock used if :identity key was provided" 
   (job-exec identity {:message {:identity "red1" :args {:machine {:hostname "red1"}}} :attempt 1 :user "ronen"}) => {:status :success}
   (provided 
     (s/get-system "red1") => {:machine {:hostname "red1"}}
     (server-conn) => {}
     (acquire-lock {} "red1" 1800000 300000) => nil :times 1
     (save-status anything :success)  => {:status :success}  :times 1
     ))


(fact "enqueue to workless queue should fail"
     (enqueue "foobar" {}) => (throws AssertionError))


(fact "jobs by envs"
   (jobs/jobs-status [:dev]) => {:jobs [{:env :dev}]}
   (provided
     (jobs/running-jobs-status) => [{:env :dev} {:env :qa}]))
