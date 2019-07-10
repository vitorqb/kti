(ns kti.db.captured-references
  (:require
   [kti.db.state :refer [*db*]]
   [kti.db.constants :as db.constants]
   [conman.core :as conman]))


(conman/bind-connection *db* "sql/captured-reference.sql")

(defn expand-filter-opts
  "Expands a map of filter opts in a nicer way to be read from the .sql file"
  [{:keys [has-article? has-review?]}]
  (cond-> {}
    (true? has-article?)  (assoc :filter.article-is-not-nil? true)
    (false? has-article?) (assoc :filter.article-is-nil? true)
    (true? has-review?)   (assoc :filter.review-is-not-nil? true)
    (false? has-review?)  (assoc :filter.review-is-nil? true)))

(defn parse-get-query-params
  "Parses the params for a get query."
  [{:keys [filter-opts] :as params}]
  (-> params
      (dissoc :filter-opts)
      (assoc :select (snip-select))
      (assoc :filter (-> filter-opts expand-filter-opts snip-filter))))

(defn get-captured-reference
  ([params] (q-get-captured-reference (assoc params :select (snip-select))))
  ([db params & rest]
   (apply q-get-captured-reference db (assoc params :select (snip-select)) rest)))

(defn get-user-captured-references [params]
  (-> params parse-get-query-params q-get-user-captured-references))

(defn count-user-captured-references [params]
  (-> params
      parse-get-query-params
      q-count-user-captured-references
      (get (keyword "count(*)"))
      (or 0)))
