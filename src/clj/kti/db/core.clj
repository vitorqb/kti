(ns kti.db.core
  (:require [kti.db.state :refer [*db*]]
            [kti.utils :as utils]
            [clojure.java.jdbc :as jdbc]
            [conman.core :as conman]
            [java-time]
            [java-time.pre-java8 :as jt]
            [kti.config :refer [env]]
            [hugsql.parameters]))

(extend-protocol jdbc/IResultSetReadColumn
  java.sql.Timestamp
  (result-set-read-column [v _2 _3]
    (.toLocalDateTime v))
  java.sql.Date
  (result-set-read-column [v _2 _3]
    (.toLocalDate v))
  java.sql.Time
  (result-set-read-column [v _2 _3]
    (.toLocalTime v)))

(extend-protocol jdbc/ISQLValue
  java.util.Date
  (sql-value [v]
    (java.sql.Timestamp. (.getTime v)))
  java.time.LocalTime
  (sql-value [v]
    (jt/sql-time v))
  java.time.LocalDate
  (sql-value [v]
    (jt/sql-date v))
  java.time.LocalDateTime
  (sql-value [v]
    (utils/date->str v))
  java.time.ZonedDateTime
  (sql-value [v]
    (jt/sql-timestamp v)))

;; Pagination implementation
(defn calculate-offset [{:keys [page page-size]}] (* (dec page) page-size))

(defn paginate-opts->sqlvec [{:keys [page-size] :as paginate-opts}]
  ["LIMIT ? OFFSET ?" page-size (calculate-offset paginate-opts)])

(defmethod hugsql.parameters/apply-hugsql-param :paginating
  [{:keys [name]} data _]
  (if-let [paginate-opts (get data (keyword name))]
    (paginate-opts->sqlvec paginate-opts)))
