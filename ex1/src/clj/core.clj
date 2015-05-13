(ns core
  (:require [ring.middleware.params :refer [wrap-params]]
            [ring.adapter.jetty     :as ring]
            [ring.middleware.defaults :refer :all]
            [clojure.java.io        :as io]
            [compojure.route        :as route]
            [compojure.response     :refer [render]]
            [compojure.core         :refer [defroutes ANY GET POST]]))




;- curl -i http://localhost:8000/


(defn home
  "Function returning a static html page"
  [req]
  (render (io/resource "index.html") req))

(defroutes app-routes
  (GET "/" [] home)              ;; home html-page
  (route/resources "/" )         ;; resources required by the html-page
  ; (route/resources "/" {:root "/public"}) ; locally this seems to be needed
  (route/not-found "Not found") ;; exception case
  )



(def handler
  (-> app-routes
      wrap-params))


(defn -main [& args]
  (let [port (Integer/parseInt (get (System/getenv) "PORT" "8080"))]
    (ring/run-jetty app-routes{:port port})))


