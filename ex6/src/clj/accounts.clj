(ns accounts
  (:require [clj-time.local :as l]
            [clj-time.core :as t]
            [clj-time.format :as f]
            [clj-time.coerce :as c]
            [schema.core :as s :include-macros true])) ;; cljs only

;;
;;; Schema
;;;
;;; Here, we use data schemata i) to validate the data between the main modules
;;; and ii) inputs to our RESTful services. Hence, we are aiming at minimal
;;; assumptions necessary for the correcntess. Especially, we are not interested
;;; in data governance across an enterprise landscape.
;;; Furthermore, we migght

;; (use '[sechma.core :as s])

(def time-stamp-formatter (f/formatters :date-time))
;; (s/validate s/Inst (to-timestamp (now)))
;; (to-string (to-timestamp (now)))

(defn time-stamp? [str]
  (and (string? str)
      (f/parse time-stamp-formatter str)))

(s/defschema time-stamp-s
  (s/pred time-stamp? 'time-stamp?))


(s/validate time-stamp-s (f/unparse time-stamp-formatter (now)))
(s/validate time-stamp-s "2010-01-01:20:12:50.459")


(s/defschema currency-code-s
  "ISO Currency codes supported by application"
  (s/enum "USD" "CHF"))


(defn value-date? [s]
  "A value data is valid (calender) date formated as YYYY-MM-DD."
  (let [pattern (re-pattern "^[0-9]{4}-[0-9]{2}-[0-9]{2}$")]
    (and (string? s)
         (re-find pattern s)
         (f/parse s))))



(s/defschema account-booking-s
   "An account booking journal entry as well as debits and credits,
    where the latter should not have a  timestamp "
   {:value-date (s/pred value-date? 'value-date?) ;; Date YYYY-MM-DD
    :amount s/Num                                 ;; amount in denominated currency
    :ccy currency-code-s                          ;; denominated currency as per code
    (s/optional-key :xref)s/Str
    (s/optional-key :ts) s/Str})


(s/defschema account-s
  "A cash account in a currency: Note incomplete"
  {:id  s/Str
   :ccy currency-code-s
   :bookings [account-booking-s]})

(s/validate s/Inst (l/format-local-time (java.util.Date.) :date-time))
(l/format-local-time (System/currentTimeMillis) :date-time)
(l/format-local-time (l/local-now) :date-time)

;; s/inst correspond to the java.util.Date object. But, I need the formatted string. Hence,
;; I need to define separate schemata




(def account-test-data
            {:id "123" :ccy "USD"
             :bookings[ {:amount 100 :value-date "2014-01-02" :ccy "USD" :xref "A1"}
                        {:amount -100 :value-date "2014-01-02" :ccy "USD" :xref "A2"}
                        {:amount 100 :value-date "2014-01-02" :ccy "USD" :xref "A3"}
                       ]})

(s/validate currency-code-s "JPY")
(s/validate account-booking-s {:amount 100 :value-date "2014" :ccy "USD"})
(s/validate account-s account-test-data)


(s/explain account-s)


;;;
;;; ********************************************************************************************
;;; ********************************************************************************************




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
   ;; (println (format "journal-entry-duplicate? >>> %s" (str entry)  (System/currentTimeMillis)))

  (if (empty? (:xref entry))
    false
    (let [a (clojure.set/project (set (list entry)) [:value-date :amount :ccy :xref])
          b (clojure.set/project (set journal) [:value-date :amount :ccy :xref])
          c (clojure.set/subset? a b)]
     ;; (println (format "journal-entry-duplicate? 2 >>> %s " (str c) (System/currentTimeMillis)))
     (clojure.set/subset?
      (clojure.set/project (set (list entry)) [:value-date :amount :ccy :xref])
      (clojure.set/project (set journal) [:value-date :amount :ccy :xref])))))



(defn add-journal-entry [accounts id entry]
  (let [j-entry (conj {:ts (l/format-local-time (l/local-now) :date-time)} entry)
        account (get-in @accounts [(keyword id)])]
    ;;(println (format "add-journal-entry >>> %s %s" (str j-entry) (System/currentTimeMillis)))
    (swap! accounts assoc-in [(keyword id) :bookings]  (vec (conj (:bookings account) j-entry )))))

(defn add-journal-entry1 [id entry]
  (add-journal-entry accounts id entry))

;;;--
;;;-- version using immutable data
;;;--------------------

(defn ^:private calculate-account-balances-1 [journal balances
                                   &{:keys [total] :or {total 0 }}]
  "Tail-recursive function to calcluate the balances of a account journal.
   Function works with plain-vanilla map as well as a record type."
  (if (not (empty? journal))
      (let [journal-entry    (first journal)
            value-date       (keyword (:value-date journal-entry))
            booking-amount   (:amount journal-entry)
            value-date-amt   (get-in balances [value-date :amount])
            value-date-total (if (number? value-date-amt) value-date-amt 0)
            balances-out     (assoc-in balances [value-date]
                                {:amount   (+ value-date-total booking-amount)
                                 :balance  (+ total booking-amount)
                                 :booking-ids (vec
                                               (conj
                                                 (get-in balances
                                                        [value-date :booking-ids])
                                                 (:id journal-entry)))})]
      (if (not (empty? (rest journal)))
         (calculate-account-balances-1 (rest journal) balances-out :total (+ total booking-amount))
         balances-out))))

(defn account-balances-1 [journal]
  (let [b (sorted-map)]
    (calculate-account-balances-1 journal b)))




