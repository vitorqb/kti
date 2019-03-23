(ns kti.routes.services.articles
  (:require [kti.routes.services.articles.base
             :refer [parse-article-data get-article-for-captured-reference]]
            [kti.db.core :as db :refer [*db*]]
            [kti.routes.services.captured-references.base :refer [get-captured-reference]]
            [kti.routes.services.reviews.base :refer [get-review-for-article]]
            [kti.validation :refer [validate]]
            [clojure.java.jdbc :refer [with-db-transaction]]))

(declare get-all-tags create-tag! get-article)

(def MAX_TAG_LENGTH 49)
(def MIN_TAG_LENGTH 2)
(def TAG_ERR_INVALID_CHARS "Tag contains invalid characters.")
(def TAG_ERR_TOO_LONG "Tag is too long.")
(def TAG_ERR_TOO_SHORT "Tag is too short")
(def ARTICLE_ERR_INVALID_CAPTURED_REFERENCE_ID
  #(format "There is no captured reference with id '%s'" %))
(def ERR-MSG-DUPLICATED-CAPTURED-REFERENCE
  #(str "An article already exists for captured reference with id" %))
(def ERR-MSG-ARTICLE-HAS-REVIEW "Article has a review associated with it.")

(defn clear-article-tags! [id] (db/delete-article-tags {:id id}))

(defn set-tags-to-article! [id tags]
  (doseq [t tags] (db/create-article-tag! {:article-id id :tag t})))

(defn validate-article-captured-reference-exists [{:keys [id-captured-reference]}]
  (when (nil? (get-captured-reference id-captured-reference))
    (ARTICLE_ERR_INVALID_CAPTURED_REFERENCE_ID id-captured-reference)))

(defn validate-unique-captured-reference [x]
  (when (get-article-for-captured-reference {:id (x :id-captured-reference)})
    (ERR-MSG-DUPLICATED-CAPTURED-REFERENCE (x :id-captured-reference))))

(defn validate-article-has-no-review [x]
  (when (get-review-for-article x) ERR-MSG-ARTICLE-HAS-REVIEW))

(defn tag-exists? [x] (-> (db/tag-exists? {:tag x}) :resp (= 1)))

(defn create-missing-tags [tags]
  (doseq [t tags]
    (when-not (tag-exists? t)
      (create-tag! t))))

(defn create-article!
  [{:keys [tags id-captured-reference] :as data}]
  (or
   (validate data
     validate-article-captured-reference-exists
     validate-unique-captured-reference)
   (with-db-transaction [t-conn *db*]
     (binding [*db* t-conn]
       (create-missing-tags tags)
       (let [article-id (-> data
                            db/create-article!
                            (get (keyword "last_insert_rowid()")))]
         (set-tags-to-article! article-id tags)
         article-id)))))

(defn update-article!
  [id {:keys [tags id-captured-reference] :as data}]
  (let [article (get-article id)]
    (or
     (validate data
       validate-article-captured-reference-exists
       #(when (not= id-captured-reference (article :id-captured-reference))
          (validate-unique-captured-reference %)))
     (with-db-transaction [t-conn *db*]
       (binding [*db* t-conn]
         (create-missing-tags tags)
         (clear-article-tags! id)
         (set-tags-to-article! id tags)
         (db/update-article! (assoc data :id id))
         nil)))))

(defn delete-article! [id]
  (or
   (validate (get-article id) validate-article-has-no-review)
   (do 
     (db/delete-article-tags {:id id})
     (db/delete-article! {:id id})
     nil)))

(defn article-exists?
  [id]
  (-> {:id id} db/article-exists? (get :resp)))

;; !!!! TODO -> use kti.validate
(defn validate-tag [x]
  (cond
    (not (string? x))
    (throw (Exception. "Can not valid a non-string tag"))
    (> (count x) MAX_TAG_LENGTH)
    TAG_ERR_TOO_LONG
    (< (count x) MIN_TAG_LENGTH)
    TAG_ERR_TOO_SHORT
    (not (re-matches #"^[1-9a-zA-Z\-\_]*$" x))
    TAG_ERR_INVALID_CHARS))

(defn create-tag!
  ([tag] (create-tag! *db* tag)) 
  ([db tag]
   ;; !!!! TODO -> Use validation framework
   (when-let [err (validate-tag tag)]
     (throw (ex-info err {:type :tag-validation-exception})))
   (db/create-tag! db {:tag tag})))

(defn count-tags [] (get (db/count-tags) (keyword "count(*)")))

(defn get-user-articles [user]
  (map parse-article-data (db/get-user-articles {:user user})))

(defn get-article
  [id]
  (some-> (db/get-article {:id id}) parse-article-data))

(defn get-tags-for-article [article]
  (->> (db/get-tags-for-article article) (map :id_tag) (into #{})))

(defn get-all-tags []
  (->> (db/get-all-tags) (map :tag) (into #{})))
