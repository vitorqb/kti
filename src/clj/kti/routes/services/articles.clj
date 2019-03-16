(ns kti.routes.services.articles
  (:require [kti.db.core :as db :refer [*db*]]
            [kti.routes.services.captured-references :refer [get-captured-reference]]
            [clojure.java.jdbc :refer [with-db-transaction]]
            [clojure.string :as string]))

(declare parse-article-data get-all-tags create-tag!)

(def MAX_TAG_LENGTH 49)
(def MIN_TAG_LENGTH 2)
(def TAG_ERR_INVALID_CHARS "Tag contains invalid characters.")
(def TAG_ERR_TOO_LONG "Tag is too long.")
(def TAG_ERR_TOO_SHORT "Tag is too short")
(def ARTICLE_ERR_INVALID_CAPTURED_REFERENCE_ID
  #(format "There is no id with captured reference '%s'" %))

(defn clear-article-tags! [id] (db/delete-article-tags {:id id}))

(defn set-tags-to-article! [id tags]
  (doseq [t tags] (db/create-article-tag! {:article-id id :tag t})))

;; !!!! TODO -> use kti.validate
(defn validate-article [{:keys [id-captured-reference]}]
  (when (nil? (get-captured-reference id-captured-reference))
    (throw (ex-info (ARTICLE_ERR_INVALID_CAPTURED_REFERENCE_ID
                     id-captured-reference)
                    {:type :invalid-captured-reference-id}))))

(defn tag-exists? [x] (-> (db/tag-exists? {:tag x}) :resp (= 1)))

(defn create-missing-tags [tags]
  (doseq [t tags]
    (when-not (tag-exists? t)
      (create-tag! t))))

(defn create-article!
  [{:keys [tags id-captured-reference] :as data}]
  ;; !!!! TODO -> Validate uniqueness of article for each captured reference
  (validate-article data)
    (with-db-transaction [t-conn *db*]
      (binding [*db* t-conn]
        (create-missing-tags tags)
        (let [article-id (-> data
                             db/create-article!
                             (get (keyword "last_insert_rowid()")))]
          (set-tags-to-article! article-id tags)
          article-id))))

(defn update-article!
  [id {:keys [tags] :as data}]
  (validate-article data)
  (with-db-transaction [t-conn *db*]
    (binding [*db* t-conn]
      (create-missing-tags tags)
      (clear-article-tags! id)
      (set-tags-to-article! id tags)
      (db/update-article! (assoc data :id id)))))

(defn delete-article! [id]
  ;; !!!! TODO -> validate no review depends on it
  (db/delete-article-tags {:id id})
  (db/delete-article! {:id id}))

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
   (when-let [err (validate-tag tag)]
     (throw (ex-info err {:type :tag-validation-exception})))
   (db/create-tag! db {:tag tag})))

(defn count-tags [] (get (db/count-tags) (keyword "count(*)")))

(defn get-all-articles
  []
  (map parse-article-data (db/get-all-articles)))

(defn get-article
  [id]
  (some-> (db/get-article {:id id}) parse-article-data))

(defn get-article-for-captured-reference
  [x]
  (some-> (db/get-article-for-captured-reference x) parse-article-data))

(defn parse-article-data [x]
  (-> x
      (select-keys [:id :description :tags])
      (assoc :action-link (:action_link x))
      (assoc :id-captured-reference (:id_captured_reference x))
      (update :tags #(and % (set (string/split % #" "))))
      (update :tags #(or % #{}))))

(defn get-tags-for-article [article]
  (->> (db/get-tags-for-article article) (map :id_tag) (into #{})))

(defn get-all-tags []
  (->> (db/get-all-tags) (map :tag) (into #{})))
