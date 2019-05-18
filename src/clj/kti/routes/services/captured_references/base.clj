(ns kti.routes.services.captured-references.base
  (:require [kti.db.core :as db :refer [*db*]]
            [kti.utils :as utils]))

(defn parse-retrieved-captured-reference
  [x]
  (into {} (map (fn [[k v]]
                  (case k
                    :classified [k (utils/int-to-bool v)]
                    :created_at [:created-at (utils/str->date v)]
                    :article_id [:article-id v]
                    :review_id  [:review-id v]
                    :review_status [:review-status (and v (utils/string->status v))]
                    [k v]))
                x)))

(defn get-captured-reference
  ([id] (get-captured-reference id nil))
  ([id user] (some-> (db/get-captured-reference *db* {:id id :user user})
                     (parse-retrieved-captured-reference))))
