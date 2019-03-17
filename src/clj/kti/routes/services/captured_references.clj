(ns kti.routes.services.captured-references
  (:require [kti.routes.services.captured-references.base
             :refer [parse-retrieved-captured-reference get-captured-reference]]
            [kti.db.core :as db :refer [*db*]]
            [java-time]
            [kti.utils :as utils]
            [kti.validation :refer [validate]]
            [kti.routes.services.articles.base
             :refer [get-article-for-captured-reference]]))

(def DELETE-ERR-MSG-ARTICLE-EXISTS
  (str  "Can not delete a captured reference used in an article."
        " Delete the article first."))

;; !!!! TODO -> Validate min length for reference
(defn create-captured-reference!
  ([x] (create-captured-reference! *db* x))

  ([db-con {:keys [reference created-at]}]
   (-> (db/create-captured-reference!
        db-con
        {:reference reference
         :created-at (or created-at (utils/now))})
       (get (keyword "last_insert_rowid()")))))

(defn get-all-captured-references
  ([] (get-all-captured-references *db*))
  ([con]
   (map parse-retrieved-captured-reference (db/get-all-captured-references con {}))))  

(defn update-captured-reference!
  ([id params] (update-captured-reference! *db* id params))
  ([con id {:keys [reference] :as args}]
   (when (not (= args {}))
     (db/update-captured-reference! con {:id id
                                         :reference reference}))))

(defn validate-no-related-article [x]
  (if ((comp not nil?) (get-article-for-captured-reference x))
    DELETE-ERR-MSG-ARTICLE-EXISTS))

(defn delete-captured-reference! [id]
  (or
   (validate (get-captured-reference id) validate-no-related-article)
   (do
     (db/delete-captured-reference! {:id id})
     nil)))

(def CAPTURED_REFERENCE_ID_ERR_NIL "Captured reference id can not be nil.")
(def CAPTURED_REFERENCE_ID_ERR_NOT_FOUND
  #(format "Captured reference with id %s was not found." %))
