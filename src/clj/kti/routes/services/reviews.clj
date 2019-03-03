(ns kti.routes.services.reviews
  (:require [kti.db.core :refer [*db*] :as db]
            [kti.routes.services.articles :refer [article-exists?]]
            [clojure.string :as str]))

(def review-status #{:pending :completed :discarded})

(defn create-review!
  "Creates a review"
  [{:keys [status id-article] :as data}]
  (assert (contains? review-status status))
  (if (not (article-exists? id-article))
    (throw (ex-info (format "Article with id %s does not exists" id-article)
                    {:type :invalid-id-article})))
  (-> data
      (update :status (comp str/upper-case name))
      db/create-review!
      (get (keyword "last_insert_rowid()"))))

(defn parse-review
  "Parses raw data for a review, retrieved from the db"
  [x]
  (into {} (map (fn [[k v]]
                  (case k
                    :id_article    [:id-article v]
                    :feedback_text [:feedback-text v]
                    :status        [k (-> v str/lower-case keyword)]
                    [k v])))
        x))

(defn get-review
  "Gets a review by id"
  [id]
  (some-> {:id id} db/get-review parse-review))
