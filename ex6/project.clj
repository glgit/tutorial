(defproject tut-ex1 "0.1.0-SNAPSHOT"
  :description "FIXME: write this!"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [ring "1.2.2"]
                 [ring/ring-defaults "0.1.4"]
                 [compojure "1.1.6"]
                 [environ "1.0.0"]
                 [liberator "0.12.2"]
                 [prismatic/schema "0.4.3"]
                 [clj-time "0.9.0"]
                 [org.clojure/data.json "0.2.6"]]

  :plugins [[lein-ring "0.8.11"]]


  :source-paths ["src/clj"]



  :ring {:handler core/handler
         :adapter {:port 8000}}


  :global-vars {*print-length* 20})
