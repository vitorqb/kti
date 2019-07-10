(ns kti.routes.services.reviews
  (:require [kti.db.state :refer [*db*]]
            [kti.db.reviews :as db.reviews]
            [kti.routes.services.articles :refer [article-exists?]]
            [kti.routes.services.articles.base :refer [get-article]]
            [kti.routes.services.reviews.base :refer :all]
            [kti.routes.services.users :refer [get-user-for]]
            [kti.validation :refer [with-validation]]
            [clojure.string :as str]))

(def review-status #{:in-progress :completed :discarded})
(def INVALID-REVIEW-STATUS #(str "Invalid value for status: " %))
(def INVALID-ID-ARTICLE #(str "Article with id " % " does not exists"))
(def DUPLICATED-REVIEW-FOR-ARTICLE
  #(str "Article with id " % " already has a review"))

(defn validate-review-status [{x :status}]
  (when-not (review-status x) (INVALID-REVIEW-STATUS x)))
(defn validate-id-article [{x :id-article}]
  (when-not (article-exists? x) (INVALID-ID-ARTICLE x)))
(defn validate-article-belongs-to-user [user {:keys [id-article]}]
  (when user
    (when (nil? (get-article id-article user))
      (INVALID-ID-ARTICLE id-article))))
(defn validate-unique-review-for-article [{x :id-article}]
  (when (get-review-for-article {:id x}) (DUPLICATED-REVIEW-FOR-ARTICLE x)))

(defn get-review
  "Gets a review by id"
  ([id] (get-review id nil))
  ([id user] (some-> {:id id :user user} db.reviews/get-review parse-review)))

(def get-all-reviews (comp (partial map parse-review) db.reviews/get-all-reviews))

(defn get-user-reviews [user] (map parse-review (db.reviews/get-user-reviews user)))

(defn create-review-validators [user]
  [validate-id-article
   #(validate-article-belongs-to-user user %)
   validate-review-status
   validate-unique-review-for-article])

(defn create-review!
  ([data] (create-review! data nil))
  ([{:keys [status id-article] :as data} user]
   (with-validation [(create-review-validators user) data]
     (-> data
         (update :status status->string)
         db.reviews/create-review!
         (get (keyword "last_insert_rowid()"))))))

(defn update-review-validators [user id-article-changed?]
  [validate-review-status
   validate-id-article
   #(validate-article-belongs-to-user user %)
   #(if id-article-changed? (validate-unique-review-for-article %))])

(defn update-review!
  [id {:keys [status id-article] :as data}]
  (let [user (get-user-for :review {:id id})
        old-id-article (-> id get-review :id-article)
        id-article-changed? (not (= id-article old-id-article))
        validators (update-review-validators user id-article-changed?)]
    (with-validation [validators data]
      (-> data (assoc :id id) (update :status status->string) db.reviews/update-review!)
      nil)))

(defn delete-review! [id] (db.reviews/delete-review! {:id id}))
