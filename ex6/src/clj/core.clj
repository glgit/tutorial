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


(defn malformed-accounts-booking? [{{method :request-method} :request :as ctx}]
  "Bookings must contain a value-date, amount, currency and an existing account, where
   the currency must match the account currency."
  (if (= :post method)
    (try
      (if-let [body (body-as-string ctx)]
        (let [booking-data (j/read-str body :key-fn keyword)]
          (if (or
             (empty? (:amount booking-data))
             (empty? (:ccy booking-data))
             (empty? (:value-date booking-data)))
           [true  {:message "booking incomplete."}]
           [false {:booking-data booking-data}]))
         [true {:message "No body"}])
      (catch Exception e
         [true {:message (format "exception: %s" (.getMessage e))}]))))



;;;-----------------------------------------------------------------------------

;; "Macro to define a REST resource in my style using the liberator library.
;;  For macroexpansion in the REPL, use liberator.core/defresource in definition"

(defmacro my-r-macro
  [r-name r-id & {:keys [malformed-fn lookup-fn duplicate-fn validate-fn update-fn]}]
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
                   (if (not (~validate-fn (keyword ~r-id) record#))
                     [true {:message "invalid entry" }]
                     [false {:record record#}])))
              [true {:message "No body"}])
          (catch Exception e#
           [true {:message (format "exception: %s" (.getMessage e#))}]))))
     :exists?
         (fn [_#]
            (if-let [res# (~lookup-fn (keyword ~r-id))]
                true
                [false {:message (format "~r-name %s not found" ~r-id)}]))
     :can-post-to-missing?
          (fn [_#] [false {:message (format "~r-name %s not found" ~r-id)}])
     :post!
          (fn [{record# :record}]
               (if (~duplicate-fn (keyword ~r-id) record#)
                 [false {:message (format "account booking %s already exists" ~r-id)}]
                 [true  {:result (~update-fn (keyword ~r-id) record#)}]))

     :location #(build-entry-url (get % :request) )
     :handle-ok (fn [_#] (j/write-str (~lookup-fn (keyword ~r-id))))))


(defn lookup-accounts [id]
  (get-in @accounts [id]))

(defn journal-entry-duplicate1? [id entry]
  (journal-entry-duplicate? entry (:bookings (get-in @accounts [id]))))


(my-r-macro accounts-bookings1-r id
    :malformed-fn malformed-accounts-booking?
    :lookup-fn lookup-accounts
    :duplicate-fn journal-entry-duplicate1?
    :validate-fn journal-entry-validate?
    :update-fn add-journal-entry1)



;; (use '[liberator.core])
;; (my-r-macro myr id)
;; (macroexpand '(my-r-macro myr id)


;; NEXT full example malformed-accounts-booking?)
;;;---------------------------------------------------------



(defresource accounts-bookings-r [acc-id ]
 :available-media-types ["application/json"]
 :allowed-methods [:get :post]
 :known-content-type? #(check-content-type % ["application/json"])
 :malformed? malformed-accounts-booking?
 :exists?
   (fn [ctx]
     ;;(println (format "exists? >>> %s %s" (str (:booking-data ctx)) (System/currentTimeMillis)))
     (if-let [acc-j  (get-in @accounts [(keyword acc-id)])]
         true ;;[true  {:account-balances  (account-balances (:bookings acc-j))}]
        [false {:message (format "Account %s not found" acc-id)}]))

 :can-post-to-missing?
    (fn [_]
      [false {:message (format "Account %s not found" acc-id)}])

  :post!
   (fn [{booking-data :booking-data}]
       ;; (println (format "post! >>> %s %s" booking-data (System/currentTimeMillis)))
       (if (journal-entry-duplicate? booking-data (:bookings (get-in @accounts [(keyword acc-id)])))
         [false {:message (format "account booking %s already exists" acc-id)}]
         [true {:result (add-journal-entry accounts (keyword acc-id) booking-data)}] ))

 ;; --- The following handler provides a result in case of a POST in addtion to the :location. The
 ;; --- example handler helps to illustrate that the POST is idempotent
 ;;:handle-created
 ;;  (fn [_]
 ;;    (j/write-str (format "{count: %s} \n" (count(get-in @accounts [(keyword acc-id) :bookings])))))

 :location #(build-entry-url (get % :request) )

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


(defroutes app-routes
  ;; REST services
  (ANY "/accounts/:id" [id] (accounts-r id))
  (ANY "/accounts/balances/:id" [id] (accounts-balances-r id))
  (ANY "/accounts/bookings/:id" [id] (accounts-bookings1-r id))
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


