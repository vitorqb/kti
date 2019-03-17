(ns kti.routes.services.reviews
  (:require [kti.db.core :refer [*db*] :as db]
            [kti.routes.services.articles :refer [article-exists?]]
            [kti.routes.services.reviews.base :refer :all]
            [clojure.string :as str]))

(def review-status #{:in-progress :completed :discarded})
;; !!!! TODO -> use kti.validate
(defn validate-review-status [x] (assert (review-status x)))
(defn validate-id-article [x]
  (when-not (article-exists? x)
    (throw (ex-info (format "Article with id %s does not exists" x)
                    {:type :invalid-id-article}))))

;; !!!! TODO -> use validation framework.
(defn create-review!
  "Creates a review"
  [{:keys [status id-article] :as data}]
  (validate-review-status status)
  (validate-id-article id-article)
  (-> data
      (update :status status->string)
      db/create-review!
      (get (keyword "last_insert_rowid()"))))

;; !!!! TODO -> use validation framework.
(defn update-review!
  [id {:keys [status id-article] :as data}]
  (validate-id-article id-article)
  (validate-review-status status)
  (-> data (assoc :id id) (update :status status->string) db/update-review!))

(defn delete-review! [id] (db/delete-review! {:id id}))

(defn get-review
  "Gets a review by id"
  [id]
  (some-> {:id id} db/get-review parse-review))

(def get-all-reviews (comp (partial map parse-review) db/get-all-reviews))
