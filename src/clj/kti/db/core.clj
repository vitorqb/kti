(ns kti.db.core
  (:require [kti.utils :as utils]
            [clojure.java.jdbc :as jdbc]
            [conman.core :as conman]
            [java-time]
            [java-time.pre-java8 :as jt]
            [mount.core :refer [defstate]]
            [kti.config :refer [env]]
            [hugsql.parameters]))

(defstate ^:dynamic *db*
          :start (conman/connect! {:jdbc-url (env :database-url)})
          :stop (conman/disconnect! *db*))

(conman/bind-connection *db* "sql/user.sql")
(conman/bind-connection *db* "sql/captured-reference.sql")

(defn get-captured-reference
  ([params] (q-get-captured-reference (assoc params :select (snip-select))))
  ([db params & rest]
   (apply q-get-captured-reference db (assoc params :select (snip-select)) rest)))

(defn get-user-captured-references [params]
  (let [final-params (assoc params :select (snip-select))]
    (q-get-user-captured-references final-params)))

(defn count-user-captured-references [params]
  (let [sql-result (q-count-user-captured-references params)
        count (get sql-result (keyword "count(*)"))]
    (or count 0)))

(conman/bind-connection *db* "sql/tags.sql")
(conman/bind-connection *db* "sql/articles.sql")

(defn get-article
  ([params] (q-get-article (assoc params :select (snip-select-article))))
  ([db params & rest]
   (apply q-get-article db (assoc params :select (snip-select-article) rest))))

(defn get-user-articles [params]
  (q-get-user-articles (assoc params :select (snip-select-article))))

(defn get-article-for-captured-reference [params]
  (q-get-article-for-captured-reference
   (assoc params :select (snip-select-article))))

(conman/bind-connection *db* "sql/reviews.sql")

(conman/bind-connection *db* "sql/tokens.sql")

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
