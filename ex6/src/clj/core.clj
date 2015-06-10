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
   (empty? item))




;;;-----------------------------------------------------------------------------

;; "Macro to define a REST resource in my style using the liberator library.
;;  For macroexpansion in the REPL, use liberator.core/defresource in definition"

(defmacro my-r-macro
  [r-name r-id r-m & {:keys [malformed-fn]}]
  (let [;;args (get-args r-m)
        f1 (partial get-item (eval r-m))         ;; evaluate to obtain the functions
        f2 (partial duplicate-item?  (eval r-m))  ;; eval returns class not the symbol
        f3 (partial valid-item?  (eval r-m))      ;;
        f4 (partial add-item (eval r-m))
        x  (f1 101)]        ;;
    (println "-" x "- " (pprint r-m) (class r-m) (pprint f1) (f1 101))
  `(do
     (def lookup-fn#    ~f1) ;; make the functions available in the name-space
     (def duplicate-fn# ~f2) ;; Otherwise, the macro refers to a non-existing function
     (def validate-fn#  ~f3) ;; a the locals from the let are not part of the macro
     (def update-fn#    ~f4) ;; NOTE Is there a better way?
     (defresource ~r-name [~r-id]
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
                   (if (not (validate-fn# (keyword ~r-id) record#))
                     [true {:message "invalid entry" }]
                     [false {:record record#}])))
              [true {:message "No body"}])
          (catch Exception e#
           [true {:message (format "exception: %s" (.getMessage e#))}]))))
     :exists?
         (fn [_#]
            (if-let [res# (lookup-fn# (keyword (str ~r-id)))]
                true
               [false {:message (format "%s %s not found..." ~r-name ~r-id)}]))
     :can-post-to-missing?
          (fn [_#] [false {:message (format "%s %s not found!" ~r-name ~r-id)}])
     :post!
          (fn [{record# :record}]
               (if (duplicate-fn# (keyword ~r-id) record#)
                 [false {:message (format "account booking %s already exists" ~r-id)}]
                 [true  {:result (update-fn# (keyword ~r-id) record#)}]))

     :location #(build-entry-url (get % :request) )
     :handle-ok (fn [_#] (j/write-str (lookup-fn# (keyword (str ~r-id)))))) )))



(def accounts-r-m
  (ResourceModel. "accounts" accounts-data account-s validation-fn?))

(def accounts-bookings-r-m
  (DependentResourceModel. "account-bookings" accounts-data account-booking-s validation-fn? :xref))


(println ">>>>" accounts-data ((partial get-item accounts-r-m) 101))

(my-r-macro accounts-r id
            accounts-r-m
            :malformed-fn malformed-request?)

(my-r-macro accounts-bookings-r id
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
  ;;(ANY "/accounts/bookings/:id" [id] (accounts-bookings-r id))
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


