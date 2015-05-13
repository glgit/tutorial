(ns account)



(def account-journal
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
                 {:id "113" :value-date "2014-01-12" :amount 100 :ccy "USD"}])



(def account
  "This is s vector of maps, so one can access the booking by position
   and attributes by keyword."
  (atom
    {:101 {:account-id 101 :currency "CHF" :bookings account-journal}}))


