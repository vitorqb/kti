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
(def REFERENCE-MIN-LENGTH 2)
(def ERR-MSG-REFERENCE-MIN-LENGTH
  (str "Reference must have a minimum length of " REFERENCE-MIN-LENGTH))

(defn validate-captured-ref-reference-min-length [{:keys [reference]}]
  (when (-> reference count (< REFERENCE-MIN-LENGTH))
    ERR-MSG-REFERENCE-MIN-LENGTH))

;; !!!! TODO -> Make user a mandatory argument
(defn create-captured-reference! [{:keys [reference created-at user] :as data}]
  (or
   (validate data validate-captured-ref-reference-min-length)
   (-> (db/create-captured-reference!
        {:reference reference
         :created-at (or created-at (utils/now))
         :id-user (:id user)})
       (get (keyword "last_insert_rowid()")))))

(defn get-all-captured-references
  ([] (get-all-captured-references *db*))
  ([con]
   (map parse-retrieved-captured-reference (db/get-all-captured-references con {}))))  

(defn update-captured-reference!
  [id args]
  (or
   (validate args validate-captured-ref-reference-min-length)
   (do (db/update-captured-reference! (assoc args :id id)) nil)))

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
