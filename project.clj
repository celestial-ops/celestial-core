(defproject celestial "0.9.1"
  :description "A launching pad for virtualized applications"
  :url "https://github.com/celestial-ops/celestial-core"
  :license  {:name "Apache License, Version 2.0" :url "http://www.apache.org/licenses/LICENSE-2.0.html"}

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [clj-config "0.2.0" ]
                 [com.narkisr/gelfino-client "0.7.0" 
                   :exclusions [com.fasterxml.jackson.core/jackson-core]]
                 ; utils
                 [me.raynes/fs "1.4.6"]
                 [robert/hooke "1.3.0"]
                 [org.clojure/core.incubator "0.1.3"]
                 [slingshot "0.10.3" ]
                 [org.clojure/core.memoize "0.5.7"]
                 [org.flatland/useful "0.10.3"]
                 [org.clojure/tools.macro "0.1.5"]
                 [org.clojure/java.data "0.1.1"]
                 ; templating
                 [selmer "0.3.4"]
                 [com.palletops/stevedore "0.8.0-beta.5"]
                 [camel-snake-kebab "0.1.2"]
                 ; logging 
                 [com.taoensso/timbre "2.6.3"]
                 [ch.qos.logback/logback-classic "1.0.13"]
                 [org.codehaus.groovy/groovy "2.1.6"]
                 ; hooks/remoting
                 [clj-http "0.7.6"]
                 [http-kit "2.1.16"]
                 [supernal "0.5.0"]
                 [conjul "0.0.2"]
                 ;api
                 [swag "0.2.7"]
                 [clj-yaml "0.4.0"]
                 [org.clojure/data.json "0.2.2" ]
                 ; ring
                 [ring-middleware-format "0.3.0"]
                 [ring/ring-jetty-adapter "1.2.0"]
                 [ring "1.3.2"]
                 [compojure "1.3.4" :exclusions  [ring/ring-core]]
                 ; ring security
                 [com.cemerick/friend "0.2.1"] 
                 [ring/ring-session-timeout "0.1.0"]
                 [ring/ring-headers "0.1.0"] 
                 ; hypervisors
                 [com.vmware/vijava "5.1" :exclusions [xml-apis]]
                 [org.pacesys/openstack4j "2.0.3"]
                 [amazonica "0.3.13" ]
                 ; persistency and model
                 [clojurewerkz/elastisch "2.0.0-beta3"]
                 [puny "0.3.0"]
                 [com.taoensso/carmine "2.11.1"] 
                 [commons-codec "1.10"]
                 [substantiation "0.2.1"]
                 [fogus/minderbinder "0.2.0"]
                 [org.clojure/core.logic "0.8.10"]
                 ; scheduling
                 [jarohen/chime "0.1.6" :exclusions [org.clojure/core.async]]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 ; remote managment
                 [org.clojure/tools.nrepl "0.2.10"]
               ]

  :exclusions [org.clojure/clojure commons-codec]

  :plugins  [[jonase/eastwood "0.2.1"] 
             [self-build "0.0.6"]
             [lein-ancient "0.6.7" :exclusions [org.clojure/clojure]] 
             [lein-tar "2.0.0" ]
             [lein-tag "0.1.0"] [lein-set-version "0.3.0"]
             [topping "0.0.2"] [self-build "0.0.3"]]

  :bin {:name "celestial"}

  :profiles {
     :refresh {
        :repl-options {
          :init-ns user
          :timeout 120000
        }

        :dependencies [[org.clojure/tools.namespace "0.2.10"] [midje "1.6.3"]
                       [redl "0.2.4"] [org.clojure/tools.trace "0.7.8"]]
        :injections  [(require '[redl core complete])]
        :resource-paths  ["src/main/resources/" "pkg/etc/celestial/"]
        :source-paths  ["dev"]
        :test-paths  []
        :jvm-opts ["-XX:MaxPermSize=256m"]
 
     }

     :dev {
        :repl-options {
          :timeout 120000
        }

        :aot [remote.capistrano remote.ruby proxmox.provider vc.provider freenas.provider
              aws.provider docker.provider physical.provider openstack.provider
              celestial.core celestial.puppet-standalone celestial.launch]
 
        :test-paths ["test" "data"]
        :source-paths  ["dev"]
        :resource-paths  ["src/main/resources/" "pkg/etc/celestial/"]
        :dependencies [[ring-mock "0.1.5"] [midje "1.6.3"]
                       [org.clojure/tools.trace "0.7.8"]
                       [org.clojure/test.check "0.7.0"]]
        :plugins [[lein-midje "3.1.3"]]
        :jvm-opts ~(into (vec (map (fn [[p v]] (str "-D" (name p) "=" v)) {:disable-conf "true" })) ["-XX:MaxPermSize=256m"])
        :set-version {
           :updates [ 
             {:path "project.clj" :search-regex #"\"target\/celestial-\d+\.\d+\.\d+\.jar"}
             {:path "src/celestial/common.clj" :search-regex #"\"\d+\.\d+\.\d+\""}]}

        :main celestial.launch
      }

     :prod {
        :resource-paths  ["src/main/resources/" "pkg/etc/celestial/"] 

        :jvm-opts ["-XX:MaxPermSize=512m"]

        :main celestial.launch
      } 
    }


  :aliases {"celestial" [ "with-profile" "prod" "do" "compile," "trampoline" "run"]
            "remote-repl" ["repl" ":connect" "celestial:7888"]
            "autotest" ["midje" ":autotest" ":filter" "-integration"] 
            "runtest" ["midje" ":filter" "-integration"] 
            "populate" ["run" "-m" "celestial.fixtures.populate"]
            ; https://github.com/stuartsierra/reloaded workflow
            "dev-repl" ["with-profile" "refresh" "do" "clean," "repl"] 
            }

  
  :repositories  {"bintray"  "http://dl.bintray.com/content/garkisr/narkisr-jars"
                  "sonatype" "http://oss.sonatype.org/content/repositories/releases"}

  :topping {
      :service "celestial"
      :app {:app-name "celestial" :src "target/celestial-0.9.1.jar"}
      :env {:roles {:remote #{{:host "celestial" :user "ubuntu" :sudo true}}}}
   } 

  :resource-paths  ["src/main/resources/"]
  :source-paths  ["src"]
  :target-path "target/"
  :test-paths  []
  :repl-options { }

)
