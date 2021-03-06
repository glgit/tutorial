(ns account)



;-- example 2 uses only a def, where here we use a atom to hold the raw data
;-- why?

(def account-journal
  "This is s vector of maps, so one can access the booking by position
   and attributes by keyword."
  (atom
    [{:id "101" :value-date "2014-01-01" :amount 100 :ccy "USD"}
     {:id "102" :value-date "2014-01-01" :amount 100 :ccy "USD"}
     {:id "103" :value-date "2014-01-03" :amount 100 :ccy "USD"}
     {:id "104" :value-date "2014-01-04" :amount 100 :ccy "USD"}
     {:id "105" :value-date "2014-01-04" :amount 100 :ccy "USD"}
     {:id "106" :value-date "2014-01-05" :amount 100 :ccy "USD"}
     {:id "107" :value-date "2014-01-06" :amount 100 :ccy "USD"}
     {:id "108" :value-date "2014-01-07" :amount 100 :ccy "USD"}
     {:id "109" :value-date "2014-01-08" :amount 100 :ccy "USD"}
     {:id "110" :value-date "2014-01-09" :amount 100 :ccy "USD"}
     {:id "111" :value-date "2014-01-10" :amount 100 :ccy "USD"}
     {:id "112" :value-date "2014-01-11" :amount 100 :ccy "USD"}
     {:id "113" :value-date "2014-01-12" :amount 100 :ccy "USD"}]))


(def account
  "This is s vector of maps, so one can access the booking by position
   and attributes by keyword."
  (atom
    {:101 {:account-id 101 :currency "CHF" :bookings @account-journal}}))


(defn ^:private calculate-account-balances [journal balances
                                   &{:keys [total] :or {total 0 }}]
  "Tail-recursive function to calcluate the balances of a account journal.
   Function works with plain-vanilla map as well as a record type."
  (if (not (empty? journal))
      (let [journal-entry    (first journal)
            value-date       (keyword (:value-date journal-entry))
            booking-amount   (:amount journal-entry)
            value-date-amt   (get-in @balances [value-date :amount])
            value-date-total (if (number? value-date-amt) value-date-amt 0)]

      (swap! balances assoc-in [value-date]
                                {:amount   (+ value-date-total booking-amount)
                                 :balance  (+ total booking-amount)
                                 :booking-ids (vec
                                               (conj
                                                 (get-in @balances
                                                        [value-date :booking-ids])
                                                 (:id journal-entry)))})
      (if (not (empty? (rest journal)))
         (calculate-account-balances (rest journal) balances :total (+ total booking-amount))
         @balances))))


(defn account-balances0 [journal]
  (let [b (atom (sorted-map))]
    (calculate-account-balances journal b)))


;; The following version interprets the journal as a set and then
;; groups the entries per value-date using the set/index function.
;; Note that this function operates on immutable data (i.e., no atom
;; etc. is necessary).

(defn account-balances [journal]
  "Takes a journal - a vector of maps - and returns an lazy sequence
   of maps with the value-date :balance amount and relevant :bookings."
  (let [iset (clojure.set/index journal [:value-date])]
    (map (fn [a b c] {:value-date a :balance b :bookings c})
        (map :value-date (map first iset))
        (map  #(apply + (map :amount %))  (map second iset))
        (map second iset))))









;; (defn ^:private calculate-account-balances-1 [journal balances
;;                                    &{:keys [total] :or {total 0 }}]
;;   "Tail-recursive function to calcluate the balances of a account journal.
;;    Function works with plain-vanilla map as well as a record type."
;;   (if (not (empty? journal))
;;       (let [journal-entry    (first journal)
;;             value-date       (keyword (:value-date journal-entry))
;;             booking-amount   (:amount journal-entry)
;;             value-date-amt   (get-in @balances [value-date :amount])
;;             value-date-total (if (number? value-date-amt) value-date-amt 0)
;;             balances-out     (assoc-in balances [value-date]
;;                                 {:amount   (+ value-date-total booking-amount)
;;                                  :balance  (+ total booking-amount)
;;                                  :booking-ids (vec
;;                                                (conj
;;                                                  (get-in balances
;;                                                         [value-date :booking-ids])
;;                                                  (:id journal-entry)))})]
;;       (if (not (empty? (rest journal)))
;;          (calculate-account-balances-1 (rest journal) balances-out :total (+ total booking-amount))
;;          balances-out))))
;;
;; (defn account-balances-1 [journal]
;;   (let [b (sorted-map)]
;;     (calculate-account-balances-1 journal b)))




