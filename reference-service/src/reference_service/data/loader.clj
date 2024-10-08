(ns reference-service.data.loader
  (:require
   [clojure.data.csv :as csv]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.tools.logging :as log]
   [medley.core :as medley]
   [next.jdbc :as jdbc]
   [next.jdbc.connection :as connection]
   [next.jdbc.sql :as sql]
   [reference-service.price.logic :as prices])
  (:import
   (java.time LocalDateTime)
   (java.util UUID)
   (com.zaxxer.hikari HikariDataSource)))

(def csv
  "./resources/s-and-p-500-companies.csv")

(def insert-stocks
  "insert into stocks
     (_id, security, sec_filings, gics_sector,
      gics_sub_industry, headquarters, first_added,
      cik, founded)
    values ")

(def select-stocks
  "select _id as ticker, security as company from stocks")

(defonce stocks
  (atom {}))

(defn cache-stocks
  "Indexes stocks by ticker"
  [stockz]
  (reset! stocks
          (->>  stockz
                (map (fn [stock]
                       (-> stock
                           (assoc :companyName (:company stock))
                           (dissoc :company))))
                (medley/index-by :ticker))))

(defn read-stocks
  [connection-like]
  (let [stocks
        (sql/query connection-like
                   [select-stocks])]
    (when (seq stocks)
      (cache-stocks stocks))))

(def account-seed
  [[22214 "Test Account 20"]
   [11413 "Private Clients Fund TTXX"]
   [42422 "Algo Execution Partners"]
   [52355 "Big Corporate Fund"]
   [62654 "Hedge Fund TXY1"]
   [10031 "Internal Trading Book"]
   [44044 "Trading Account 1"]])

(def insert-account
  "insert into accounts
     (_id, name)
    values
     (?,?)")

(defn do-insert
  [jdbc-ds data]
  (let [stocks (mapv
                (fn [line]
                  (update line 7 #(Integer/parseInt %)))
                data)
        values-clause (str/join ", " (repeat (count stocks) "(?,?,?,?,?,?,?,?,?)"))]
    (with-open [conn (jdbc/get-connection jdbc-ds)]
      (prices/set-system-time conn (.minusDays (LocalDateTime/now) 365))
      (sql/query conn ["BEGIN"])
      (jdbc/execute! conn (reduce
                           into
                           [(str insert-stocks values-clause)]
                           stocks))
      (run! #(jdbc/execute! conn (into [insert-account] %))
            account-seed)
      (sql/query conn ["COMMIT"])
      (read-stocks conn))))

(defn populate-stocks-and-accounts
  [jdbc-ds]
  (with-open [rdr (io/reader csv)]
    (let [data-lines (drop 1 (csv/read-csv rdr))
          input-stock-count (count data-lines)
          stocks (read-stocks jdbc-ds)]
      (if (pos? (count stocks))
        (log/infof "Stocks already populated, there are %d stocks" (count stocks))
        (do
          (log/infof "Populating %d stocks" input-stock-count)
          (do-insert jdbc-ds data-lines))))))

(def trade-seed
  [{:id "TRADE-22214-AABBCC" :security "IBM" :accountId 22214 :unitPrice 123 :quantity 100 :side "Buy"}
   {:id "TRADE-22214-DDEEFF" :security "MS" :accountId 22214 :unitPrice 88 :quantity 1000 :side "Buy"}
   {:id "TRADE-22214-GGHHII" :security "C" :accountId 22214 :unitPrice 321 :quantity 2000 :side "Buy"}
   {:id "TRADE-52355-AABBCC" :security "BAC" :accountId 52355 :unitPrice 20 :quantity 2400 :side "Buy"}])

(def trade-dates
  (let [hour (* 60 60 1000)
        day (* 24 hour)
        t0 (- (System/currentTimeMillis) (* 30 day))]
    (mapv
     (fn [t]
       {:created (+ (* t day) t0)
        :updated (+ (* t day) t0 hour hour)}) ; settles 2 hours after trade
     (range (count trade-seed)))))

(defn trade-module-trades
  [jdbc-ds]
  (run! (fn [trade-dates]
          (prices/save-trade jdbc-ds
                             trade-dates))
        (map merge trade-seed trade-dates)))

(def price-at
  "select price from stock_prices
   for valid_time
   from ? to ?
   where _id = ?")

(defn stock-price-for-date
  [jdbc-ds stock date]
  (let [price (:price
               (first
                (sql/query jdbc-ds
                           [price-at
                            (prices/local-date date)
                            (prices/local-date date)
                            stock])))]
    (log/infof "Price for %s on %s is %s" stock date price)
    price))

(defn weeks-worth-of-trades
  [jdbc-ds]
  (let [hour (* 60 60 1000)
        day (* 24 hour)
        week (* 7 day)
        start (- (System/currentTimeMillis) (* 15 day))
        all-stocks (keys @stocks)
        account-stocks (map (fn [account]
                              [account
                               (mapv (fn [ticker]
                                       [ticker (inc (rand-int 667))])
                                     (take 7 (shuffle all-stocks)))])
                            (map first account-seed))
        trades (reduce into []
                       (mapcat (fn [[account stock-quantities]]
                                 (map-indexed
                                  (fn [offset [stock quantity]]
                                    (let [id (str (UUID/randomUUID))
                                          buy-date (+ start (* offset day))
                                          sell-date (+ buy-date week)
                                          half-buy-quantity (int (/ (inc quantity) 2))
                                          sell-quantity (if (> (rand-int 10) 6)
                                                          quantity
                                                          (+ half-buy-quantity
                                                             (rand-int half-buy-quantity)))]
                                      [{:created buy-date
                                        :updated (+ buy-date hour hour)
                                        :id id
                                        :accountId account
                                        :security stock
                                        :unitPrice (stock-price-for-date jdbc-ds stock buy-date)
                                        :quantity quantity
                                        :side "Buy"}
                                       {:created sell-date
                                        :updated (+ sell-date hour hour)
                                        :id id
                                        :accountId account
                                        :security stock
                                        :unitPrice (stock-price-for-date jdbc-ds stock sell-date)
                                        :quantity sell-quantity
                                        :side "Sell"}]))
                                  stock-quantities))
                               account-stocks))]
    (run! #(prices/save-trade jdbc-ds %)
          trades)))

(defn seed-trades
  [jdbc-ds]
  (if (-> (sql/query jdbc-ds
                       ["select count(*) as cnt from trades"])
            first
            :cnt
            (> 4))
      (log/info "Database had already been seeded")
      (do
        (log/info "Seeding trades")
        (trade-module-trades jdbc-ds)
        (weeks-worth-of-trades jdbc-ds))))

(defn get-stock
  [jdbc-ds ticker]
  (when (nil? @stocks)
    (read-stocks jdbc-ds))
  (get @stocks
       (when ticker
         (str/upper-case ticker))))

(defn get-all-stocks
  [jdbc-ds]
  (when (nil? @stocks)
    (read-stocks jdbc-ds))
  (vals @stocks))

(comment
  (def jdbc-url (connection/jdbc-url
                 {:dbtype "postgresql"
                  :dbname "traderX"
                  :host "localhost"
                  :port 18099
                  :useSSL false}))
  (def jdbc-ds (connection/->pool HikariDataSource
                                  {:jdbcUrl jdbc-url
                                   :maxLifetime 60000}))
  (seed-trades jdbc-ds)
  (jdbc/execute! jdbc-ds ["select *, _valid_from from trades for all valid_time"])
  (jdbc/execute! jdbc-ds ["delete from accounts"])
  (with-open [rdr (io/reader csv)]
    (do-insert jdbc-ds (drop 1 (csv/read-csv rdr))))

  (def stocks (cache-stocks (read-stocks jdbc-ds)))
  #_1)
