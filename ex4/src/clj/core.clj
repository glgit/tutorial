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
            [accounts :refer :all]))



(defresource accounts-r [id]
	:available-media-types ["application/json"]
	:allowed-methods [:get ]
	:exists?
		(fn [_]
			(if-let [acc-j  (get-in @accounts [(keyword id)])]
				[true  {:account  acc-j}]
				[false {:message (format "Account %s not found" id)}]))
	:handle-ok
		(fn [{acc-j :account}]
			(j/write-str acc-j)))

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


(defn malformed-accounts-booking? [{{method :request-method} :request :as ctx}]
  "Bookings must contain a value-date, amount, currency and an existing account, where
   the currency must match the account currency."
  (if (= :post method)
    (let [booking-data (j/read-str (body-as-string ctx) :key-fn keyword)]
       (if (or
             (empty? (:amount booking-data))
             (empty? (:ccy booking-data))
             (empty? (:value-date booking-data)))
           [true  {:message "Booking incomplete."}]
           [false {:booking-data booking-data}]))
        false))


;; (defn malformed-accounts-booking? [{{method :request-method} :request :as ctx}]
;;   "Bookings must contain a value-date, amount, currency and an existing account, where
;;    the currency must match the account currency."
;;   (if (= :post method)
;;     (let [booking-data (j/read-str ctx)
;;           account      (get-in @accounts [(keyword id)])
;;           account-ccy  (:currency account)]
;;      (if (empty? account)
;;        [true {:message (format "Account %s not found." id)}]
;;        (if (or (not (= (:currency account)(:currency booking-data)))
;;              (empty? (:amount booking-data))
;;              (empty? (:currency booking-data))
;;              (empty? (:value-date booking-data)))
;;            [true {:message "Booking incomplete."}]
;;            [false {:booking-data booking-data}])
;;         false))
;;      false))
;;


(defresource accounts-bookings-r [acc-id]
 :available-media-types ["application/json"]
 :allowed-methods [:get :post]
 :malformed? malformed-accounts-booking?
 :exists?
   (fn [ctx]
     (println (format "exists? >>> %s %s" (str (:booking-data ctx)) (System/currentTimeMillis)))
     (if-let [acc-j  (get-in @accounts [(keyword acc-id)])]
         true ;;[true  {:account-balances  (account-balances (:bookings acc-j))}]
        [false {:message (format "Account %s not found" acc-id)}]))

;;  :conflict?
;;    (fn [{booking-data :booking-data}]
;;      (println (format "conflict? >>> %s %s" booking-data (System/currentTimeMillis)))
;;      (if (not (journal-entry-duplicate? booking-data (:bookings (get-in @accounts [(keyword acc-id)]))))
;;           [true]
;;           [false {:message (format "account booking %s already exists" acc-id)}]))
 :post!
   (fn [{booking-data :booking-data}]
       (println (format "post! >>> %s %s" booking-data (System/currentTimeMillis)))
       (if (journal-entry-duplicate? booking-data (:bookings (get-in @accounts [(keyword acc-id)])))
         [false {:message (format "account booking %s already exists" acc-id)}]
         [true {:result (add-journal-entry accounts (keyword acc-id) booking-data)}] ))

 :handle-ok
   (fn [_]
     (j/write-str (get-in @accounts [(keyword acc-id) :bookings]))))


(defresource accounts-balances-r [id]
	:available-media-types ["application/json"]
	:allowed-methods [:get]
	:exists?
		(fn [_]
      (if-let [acc-j  (get-in @accounts [(keyword id)])]
				[true  {:account-balances  (account-balances (:bookings acc-j))}]
				[false {:message (format "Account %s not found" id)}]))
	:handle-ok
		(fn [{bal-j :account-balances}]
			(j/write-str bal-j)))


(defn home
  [req]
  (render (io/resource "index.html") req))

;; (defroutes app-routes
;;   ;; REST services
;;   ;;(ANY "/accounts/:id" [:id] (accounts-r id))
;;   ;;(ANY "/accounts/bookings/:id" [:id] (accounts-bookings-r id))
;;   (ANY "/accounts/balances/:id" [:id] (accounts-balances-r id))
;;   ;; static content
;;   (GET "/" [] home)
;;   (route/resources "/" )
;;   (route/not-found "Not found")
;;   )

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


