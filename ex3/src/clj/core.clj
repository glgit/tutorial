(ns core
  (:require [ring.middleware.params :refer [wrap-params]]
            [ring.adapter.jetty     :as ring]
            [ring.middleware.defaults :refer :all]
            [clojure.java.io        :as io]
            [compojure.route        :as route]
            [compojure.response     :refer [render]]
            [compojure.core         :refer [defroutes ANY GET POST]]
            [liberator.core         :refer [resource defresource]]
            [clojure.data.json      :as j]
            [account :refer :all]))



(defresource accounts-r [id]
	:available-media-types ["application/json"]
	:allowed-methods [:get ]
	:exists?
		(fn [_]
			(if-let [acc-j  (get-in @account [(keyword id)])]
				[true  {:account  acc-j}]
				[false {:message (format "Account %s not found" id)}]))
	:handle-ok
		(fn [{acc-j :account}]
			(j/write-str acc-j)))

;- curl -i http://localhost:8000/accounts/101  returns the account object
;- curl -i http://localhost:8000/accounts/1012 returns a 404
;- curl -i http://localhost:8000/accounts/balances/101

(defresource accounts-balances-r [id]
	:available-media-types ["application/json"]
	:allowed-methods [:get ]
	:exists?
		(fn [_]
      (if-let [acc-j  (get-in @account [(keyword id)])]
				[true  {:account-balances  (account-balances (:bookings acc-j))}]
				[false {:message (format "Account %s not found" id)}]))
	:handle-ok
		(fn [{bal-j :account-balances}]
			(j/write-str bal-j)))


(defn home
  [req]
  (render (io/resource "index.html") req))

(defroutes app-routes
  ;; REST services
  (ANY "/accounts/:id" [id] (accounts-r id))
  (ANY "/accounts/balances/:id" [id] (accounts-balances-r id))
  ;; static content
  (GET "/" [] home)
  (route/resources "/" )
  (route/not-found "Not found")
  )



(def handler
  (-> app-routes
      wrap-params))


(defn -main [& args]
  (let [port (Integer/parseInt (get (System/getenv) "PORT" "8080"))]
    (ring/run-jetty app-routes{:port port})))


