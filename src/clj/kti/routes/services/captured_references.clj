(ns kti.routes.services.captured-references
  (:require [kti.routes.services.captured-references.base
             :refer [parse-retrieved-captured-reference get-captured-reference]]
            [kti.db.state :refer [*db*]]
            [kti.db.core :as db]
            [kti.db.constants :as db.constants]
            [kti.db.captured-references :as db.cap-refs]
            [java-time]
            [kti.utils :as utils]
            [kti.validation :refer [with-validation]]
            [kti.routes.services.articles.base
             :refer [get-article-for-captured-reference]]))

(def DELETE-ERR-MSG-ARTICLE-EXISTS
  (str  "Can not delete a captured reference used in an article."
        " Delete the article first."))
(def REFERENCE-MIN-LENGTH 2)
(def ERR-MSG-REFERENCE-MIN-LENGTH
  (str "Reference must have a minimum length of " REFERENCE-MIN-LENGTH))

(defn query-params->filter-opts
  "Extract a map with `filter-opts` form a map of `query-params`"
  [{:keys [has-article has-review]}]
  {:has-article? (if (nil? has-article) ::db.constants/nofilter has-article)
   :has-review? (if (nil? has-review) ::db.constants/nofilter has-review)})

(defn validate-captured-ref-reference-min-length [{:keys [reference]}]
  (when (-> reference count (< REFERENCE-MIN-LENGTH))
    ERR-MSG-REFERENCE-MIN-LENGTH))

(defn create-captured-reference! [{:keys [reference created-at user] :as data}]
  (with-validation [[validate-captured-ref-reference-min-length] data]
    (-> (db.cap-refs/create-captured-reference!
         {:reference reference
          :created-at (or created-at (utils/now))
          :id-user (:id user)})
        (get (keyword "last_insert_rowid()")))))

(defn get-user-captured-references
  ([user] (get-user-captured-references user nil))
  ([user {:keys [paginate-opts filter-opts]}]
   (let [opts {:user user :paginate-opts paginate-opts :filter-opts filter-opts}
         raw-results (db.cap-refs/get-user-captured-references opts)
         results (map parse-retrieved-captured-reference raw-results)]
     (if paginate-opts
       (let [total-items (or (db.cap-refs/count-user-captured-references opts)
                             0)]
         (assoc paginate-opts :items results :total-items total-items))
       results))))

(defn update-captured-reference! [id args]
  (with-validation [[validate-captured-ref-reference-min-length] args]
    (db.cap-refs/update-captured-reference! (assoc args :id id))
    nil))

(defn validate-no-related-article [x]
  (if ((comp not nil?) (get-article-for-captured-reference x))
    DELETE-ERR-MSG-ARTICLE-EXISTS))

(defn delete-captured-reference! [id]
  (let [captured-reference (get-captured-reference id)
        validators [validate-no-related-article]]
    (with-validation [validators captured-reference]
      (db.cap-refs/delete-captured-reference! {:id id})
      nil)))

(def CAPTURED_REFERENCE_ID_ERR_NIL "Captured reference id can not be nil.")
(def CAPTURED_REFERENCE_ID_ERR_NOT_FOUND
  #(format "Captured reference with id %s was not found." %))
