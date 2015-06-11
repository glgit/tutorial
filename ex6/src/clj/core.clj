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
            [accounts :refer :all])
  (:use [accounts :only (->ResourceModel)]
        [clojure.pprint])
  (:import (accounts ResourceModel DependentResourceModel)))


;- curl -i http://localhost:8000/accounts/101  returns the account object
;- curl -i http://localhost:8000/accounts/1012 returns a 404
;- curl -i http://localhost:8000/accounts/balances/101


;; convert the body to a reader. Useful for testing in the repl
;; where setting the body to a string is much simpler.
(defn body-as-string [ctx]
  (if-let [body (get-in ctx [:request :body])]
    (condp instance? body
      java.lang.String body
      (slurp (io/reader body)))))


;; For PUT and POST check if the content type is json.
(defn check-content-type [ctx content-types]
  (if (#{:put :post} (get-in ctx [:request :request-method]))
    (or
     (some #{(get-in ctx [:request :headers "content-type"])}
           content-types)
     [false {:message "Unsupported Content-Type"}])
    true))


(defn build-entry-url [request & id]
   (format "%s://%s:%s%s%s"
                (name (:scheme request))
                (:server-name request)
                (:server-port request)
                (:uri request)
                (str ""(when id (str "/" id)))))


(defn malformed-request? [item]
   "Function to validate from a REST point of view."
   (empty? item))




;;;-----------------------------------------------------------------------------

;; "Macro to define a REST resource in my style using the liberator library.
;;  For macroexpansion in the REPL, use liberator.core/defresource in definition"

(defmacro defresource-macro
  [r-name r-id r-m & {:keys [malformed-fn]}]
  (let []
    (println r-name r-id)
  `(defresource ~r-name [~r-id]
     :available-media-types ["application/json"]
     :allowed-methods [:get :post]
     :known-content-type? #(check-content-type % ["application/json"])
     :malformed?
       (fn [{{method# :request-method} :request :as ctx#}]
         (if (= :post method#)
           (try
            (if-let [body# (body-as-string ctx#)]
              (let [record# (j/read-str body# :key-fn keyword)]
                 (if (~malformed-fn record#)
                   [true  {:message "booking incomplete."}]
                   (if (not (valid-item? ~r-m ~r-id record#))
                     [true  {:message "invalid entry" }]
                     [false {:record record#}])))
              [true {:message "No body"}])
          (catch Exception e#
           [true {:message (format "exception: %s" (.getMessage e#))}]))))
     :exists?
         (fn [_#]
            (if-let [res# (get-item ~r-m ~r-id)]
               [true res#]
               [false {:message (format "%s %s not found..." (get-name ~r-m) ~r-id)}]))
     :can-post-to-missing?
          (fn [_#] [false {:message (format "%s %s not found!" (get-name ~r-m) ~r-id)}])
     :post!
          (fn [{record# :record}]
               (if (duplicate-item? ~r-m ~r-id record#)
                 [false {:message (format "account booking %s already exists" ~r-id)}]
                 [true  {:result (add-item ~r-m ~r-id record#)}]))

     :location #(build-entry-url (get % :request) )
     :handle-ok (fn [_#] (j/write-str (get-item ~r-m ~r-id))))))



(def accounts-r-m
  (ResourceModel. "accounts" accounts-data account-s nil))

(def accounts-bookings-r-m
  (DependentResourceModel. "account-bookings" accounts-data account-booking-s
                           booking-validation-fn? :bookings :xref))


(defresource-macro accounts-r id
            accounts-r-m
            :malformed-fn malformed-request?)

(defresource-macro accounts-bookings-r id
            accounts-bookings-r-m
            :malformed-fn malformed-request?)


(defresource accounts-balances-r [id]
	:available-media-types ["application/json"]
	:allowed-methods [:get]
	:exists?
		(fn [_]
      (if-let [acc-j  (get-in @accounts-data [(keyword id)])]
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
  (ANY "/accounts/bookings/:id" [id] (accounts-bookings-r id))
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


