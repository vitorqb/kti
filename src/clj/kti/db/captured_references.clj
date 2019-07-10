(ns kti.db.captured-references
  (:require
   [kti.db.state :refer [*db*]]
   [conman.core :as conman]))


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
