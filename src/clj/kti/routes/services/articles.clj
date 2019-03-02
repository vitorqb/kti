(ns kti.routes.services.articles
  (:require [kti.db.core :as db :refer [*db*]]
            [clojure.java.jdbc :refer [with-db-transaction]]
            [clojure.string :as string]))

(declare parse-article-data get-all-tags create-tag!)

;; !!!! TODO -> return tags as set, not array
(def MAX_TAG_LENGTH 49)
(def MIN_TAG_LENGTH 2)
(def TAG_ERR_INVALID_CHARS "Tag contains invalid characters.")
(def TAG_ERR_TOO_LONG "Tag is too long.")
(def TAG_ERR_TOO_SHORT "Tag is too short")

(defn create-article!
  [{:keys [tags] :as data}]
  (let [all-tags       (get-all-tags)
        tag-exists?    #(all-tags %)
        tags-to-create (remove tag-exists? tags)]
    (with-db-transaction [t-conn *db*]
      (doseq [t tags-to-create]
        (create-tag! t-conn t))
      (let [article-id (-> data
                           (dissoc :tags)
                           ((partial db/create-article! t-conn))
                           (get (keyword "last_insert_rowid()")))]
        (doseq [t tags]
          (db/create-article-tag! t-conn {:article-id article-id :tag t}))
        article-id))))

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
