(ns kti.routes.services.reviews
  (:require [kti.db.core :refer [*db*] :as db]
            [kti.routes.services.articles :refer [article-exists?]]
            [kti.routes.services.articles.base :refer [get-article]]
            [kti.routes.services.reviews.base :refer :all]
            [kti.routes.services.users :refer [get-user-for]]
            [kti.validation :refer [validate]]
            [clojure.string :as str]))

(def review-status #{:in-progress :completed :discarded})
(def INVALID-REVIEW-STATUS #(str "Invalid value for status: " %))
(def INVALID-ID-ARTICLE #(str "Article with id " % " does not exists"))

(defn validate-review-status [{x :status}]
  (when-not (review-status x) (INVALID-REVIEW-STATUS x)))
(defn validate-id-article [{x :id-article}]
  (when-not (article-exists? x) (INVALID-ID-ARTICLE x)))
(defn validate-article-belongs-to-user [user {:keys [id-article]}]
  (when user
    (when (nil? (get-article id-article user))
      (INVALID-ID-ARTICLE id-article))))

(defn create-review!
  ([data] (create-review! data nil))
  ([{:keys [status id-article] :as data} user]
   (or
    (validate data
      validate-id-article
      #(validate-article-belongs-to-user user %)
      validate-review-status)
    (-> data
        (update :status status->string)
        db/create-review!
        (get (keyword "last_insert_rowid()"))))))

(defn update-review!
  [id {:keys [status id-article] :as data}]
  (let [user (get-user-for :review {:id id})]
    (or
     (validate data
       validate-review-status
       validate-id-article
       #(validate-article-belongs-to-user user %))
     (do
       (-> data (assoc :id id) (update :status status->string) db/update-review!)
       nil))))

(defn delete-review! [id] (db/delete-review! {:id id}))

(defn get-review
  "Gets a review by id"
  ([id] (get-review id nil))
  ([id user] (some-> {:id id :user user} db/get-review parse-review)))

(def get-all-reviews (comp (partial map parse-review) db/get-all-reviews))
