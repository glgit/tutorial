(ns accounts
  (:require [clj-time.local :as l]))

;-- example 2 uses only a def, where here we use a atom to hold the raw data
;-- why?

(def account-journal
  "This is s vector of maps, so one can access the booking by position
   and attributes by keyword."
  (atom
    [{:xref "101" :value-date "2014-01-01" :amount 100 :ccy "USD" :ts "20140101-144327456"}
     {:xref "102" :value-date "2014-01-01" :amount 100 :ccy "USD" :ts "20140101-144327456"}
     {:xref "103" :value-date "2014-01-03" :amount 100 :ccy "USD" :ts "20140101-144327456"}
     {:xref "104" :value-date "2014-01-04" :amount 100 :ccy "USD" :ts "20140101-144327456"}
     {:xref "105" :value-date "2014-01-04" :amount 100 :ccy "USD" :ts "20140101-144327456"}
     {:xref "106" :value-date "2014-01-05" :amount 100 :ccy "USD" :ts "20140101-144327456"}
     {:xref "107" :value-date "2014-01-06" :amount 100 :ccy "USD" :ts "20140101-144327456"}
     {:xref "108" :value-date "2014-01-07" :amount 100 :ccy "USD" :ts "20140101-144327456"}
     {:xref "109" :value-date "2014-01-08" :amount 100 :ccy "USD" :ts "20140101-144327456"}
     {:xref "110" :value-date "2014-01-09" :amount 100 :ccy "USD" :ts "20140101-144327456"}
     {:xref "111" :value-date "2014-01-10" :amount 100 :ccy "USD" :ts "20140101-144327456"}
     {:xref "112" :value-date "2014-01-11" :amount 100 :ccy "USD" :ts "20140101-144327456"}
     {:xref "113" :value-date "2014-01-12" :amount 100 :ccy "USD" :ts "20140101-144327456"}]))



(def accounts
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


(defn journal-entry-duplicate? [entry journal]
  "Duplicate, if entry with an :xref is already in journal. Otherwise,
   we assume here simply that it is not a duplicate (and do not check
   the update timestamp :ts."
  (println (format "journal-entry-duplicate? >>> %s" (str entry)  (System/currentTimeMillis)))

  (if (empty? (:xref entry))
    false
    (let [a (clojure.set/project (set (list entry)) [:value-date :amount :ccy :xref])
          b (clojure.set/project (set journal) [:value-date :amount :ccy :xref])
          c (clojure.set/subset? a b)]
     (println (format "journal-entry-duplicate? 2 >>> %s " (str c) (System/currentTimeMillis)))
     (clojure.set/subset?
      (clojure.set/project (set (list entry)) [:value-date :amount :ccy :xref])
      (clojure.set/project (set journal) [:value-date :amount :ccy :xref])))))


(defn add-journal-entry [accounts id entry]
  (let [j-entry (conj {:ts (l/format-local-time (l/local-now) :date-time)} entry)
        account (get-in @accounts [(keyword id)])]
    (println (format "add-journal-entry >>> %s %s" (str j-entry) (System/currentTimeMillis)))
    (swap! accounts assoc-in [(keyword id) :bookings]  (vec (conj (:bookings account) j-entry )))))




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




