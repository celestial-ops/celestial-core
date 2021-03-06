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

(ns re-core.api
  (:refer-clojure :exclude [type])
  (:require 
    [re-core.roles :refer (admin su)]
    [ring.middleware.format :refer (wrap-restful-format)]
    [ ring.middleware.format-params :refer (wrap-json-kw-params)]
    [ring.middleware.session-timeout :refer (wrap-idle-session-timeout)]
    [ring.middleware.x-headers :refer (wrap-frame-options)]
    [swag.model :refer (wrap-swag)]
    [re-core.common :refer (import-logging get! get* version)]
    [compojure.core :refer (defroutes routes)] 
    [clojure.core.strint :refer (<<)]
    [re-core.api  
      [monitoring :refer (metrics)]
      [jobs :refer (jobs)]
      [actions :refer (actions actions-ro)]
      [types :refer (types types-ro)]
      [audits :refer (audits-ro audits)]
      [systems :refer (systems environments systems-admin templates)] 
      [stacks :refer (stacks stacks-ro)] 
      [users :refer (users quotas users-ro users-current)]
      [ui :refer (public sessions)]]
    [re-core.security :as sec]
    [ring.middleware.session.cookie :refer (cookie-store)]
    [ring.middleware.session :refer (wrap-session)]
    [swag.core :refer (swagger-routes)]
    [compojure.handler :as handler]
    [cemerick.friend :as friend]
    [compojure.route :as route]
    ))


(import-logging)

(defroutes app-routes
  templates systems types-ro 
  audits-ro actions-ro stacks-ro
  environments jobs sessions users-current
  (friend/wrap-authorize metrics su)
  (friend/wrap-authorize users-ro su)
  (friend/wrap-authorize users admin)
  (friend/wrap-authorize systems-admin admin)
  (friend/wrap-authorize stacks admin)
  (friend/wrap-authorize actions admin)
  (friend/wrap-authorize types admin)
  (friend/wrap-authorize audits admin)
  (friend/wrap-authorize quotas admin)

  (route/not-found "Not Found"))

(defn error-wrap
  "A catch all error handler"
  [app]
  (fn [req]
    (try 
      (app req)
      (catch Throwable e 
        (error e)
        {:body (<< "Unexpected error ~(.getMessage e) of type ~(class e) contact re-core admin for more info") :status 500}))))

(defn force-https [rs]
  (binding [friend/*default-scheme-ports* {:http (get! :re-core :port) :https (get! :re-core :https-port)}]
    (friend/requires-scheme rs :https)))

(defn compose-routes
  "Composes celetial apps" 
  [secured?]
  (let [rs (routes public (swagger-routes version) (if secured? (sec/secured-app app-routes) app-routes) )]
    (if secured? 
      (force-https rs) rs)))

(defn app [secured?]
  "The api routes, secured? will enabled authentication"
  (-> (compose-routes secured?) 
      (wrap-swag) 
      (wrap-idle-session-timeout {:timeout (or (get* :re-core :session-timeout) 600) :timeout-response (ring.util.response/redirect (<< "/login"))})
      (wrap-session {:cookie-name "re-core" :store (cookie-store) :cookie-attrs {:secure true :max-age 3600}})
      (wrap-json-kw-params)
      (wrap-restful-format :formats [:json-kw :edn :yaml-kw :yaml-in-html])
      (handler/api)
      (wrap-frame-options :deny)
      (error-wrap)))
