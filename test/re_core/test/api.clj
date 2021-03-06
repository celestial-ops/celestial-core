(ns re-core.test.api
  "These scenarios describe how the API works and mainly validates routing"
  (:refer-clojure :exclude [type])
  (:require 
    [re-core.model :as m]
    [re-core.fixtures.data :as d]
    [re-core.api.users :refer (into-persisted)]
    [re-core.jobs :as jobs]
    [re-core.persistency.systems :as s] 
    [clojure.core.strint :refer (<<)]
    [cemerick.friend :as friend]
    [re-core.roles :as roles]
    [re-core.persistency [types :as t] [users :as u]])
  (:use 
    midje.sweet ring.mock.request
    [re-core.api :only (app)]))

(def non-sec-app (app false))

(fact "system get"
  (non-sec-app (request :get (<< "/systems/1"))) => (contains {:status 200}) 
  (provided (s/get-system "1") => "foo"))

(fact "getting host type"
  (non-sec-app 
    (header 
      (request :get (<< "/systems/1/type")) "accept" "application/json")) => (contains {:status 200})
  (provided 
    (s/get-system "1") => {:type "redis"} 
    (t/get-type "redis") => {:classes {:redis {:append true}}}))

(let [machine {:type "redis" :machine {:host "foo"}} type {:run-opts nil :classes {:redis {}}}]
  (fact "provisioning job"
    (non-sec-app (request :post "/jobs/provision/1" {})) => (contains {:status 200})
    (provided 
      (u/op-allowed? "provision" nil) => true
      (s/system-exists? "1") => true
      (s/get-system "1")  => machine
      (s/get-system "1" :env)  => :dev
      (t/get-type "redis") => type
      (jobs/enqueue "provision" {:identity "1" :args [type (assoc machine :system-id 1)] :tid nil :env :dev :user nil}) => nil)))

(fact "staging job" 
  (non-sec-app (request :post "/jobs/stage/1")) => (contains {:status 200})
  (provided
    (u/op-allowed? "stage" nil) => true
    (s/system-exists? "1") => true
    (t/get-type "redis") => {:puppet-module "bar"}
    (s/get-system "1") => {:type "redis"}
    (s/get-system "1" :env)  => :dev
    (jobs/enqueue "stage" {:identity "1" :args [{:puppet-module "bar"} {:system-id 1 :type "redis"}] :tid nil :env :dev :user nil}) => nil))

(fact "creation job"
   (non-sec-app (request :post "/jobs/create/1"))  => (contains {:status 200})
   (provided 
     (u/op-allowed? "create" nil) => true
     (s/system-exists? "1") => true
     (s/get-system "1")  => {}
     (s/get-system "1" :env)  => :dev
     (jobs/enqueue "create" {:identity "1" :args [{:system-id 1}] :tid nil :env :dev :user nil}) => nil))

(let [user (merge d/admin {:roles ["admin"] :envs ["dev" "qa"]}) 
      ops-vec (into []  m/operations)]
  (fact "user conversion"
    (dissoc (into-persisted user) :password) => 
      {:envs [:dev :qa] :roles #{:re-core.roles/admin} :username "admin" :operations ops-vec}
    (into-persisted (dissoc user :password)) => 
      {:envs [:dev :qa] :roles #{:re-core.roles/admin} :username "admin" :operations ops-vec}))
