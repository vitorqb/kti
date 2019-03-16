(ns kti.routes.services.captured-references
  (:require [kti.db.core :as db :refer [*db*]]
            [java-time]
            [kti.utils :as utils]
            [kti.validation :refer [validate]]))

(declare parse-retrieved-captured-reference)

(def DELETE-ERR-MSG-ARTICLE-EXISTS
  (str  "Can not delete a captured reference used in an article."
        " Delete the article first."))

(defn create-captured-reference!
  ([x] (create-captured-reference! *db* x))

  ([db-con {:keys [reference created-at]}]
   (-> (db/create-captured-reference!
        db-con
        {:reference reference
         :created-at (or created-at (utils/now))})
       (get (keyword "last_insert_rowid()")))))

(defn get-captured-reference
  ([id] (get-captured-reference *db* id))

  ([db-con id]
   (some-> (db/get-captured-reference db-con {:id id})
           (parse-retrieved-captured-reference))))

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

;; !!!! TODO -> Refactor to aovid cyclical import
(defn make-validate-no-related-article [get-article-for-captured-reference]
  (fn validate-no-related-article [x]
    (if ((comp not nil?) (get-article-for-captured-reference x))
      DELETE-ERR-MSG-ARTICLE-EXISTS)))

(defn make-delete-captured-reference! [validate-no-related-article]
  (fn delete-captured-reference! [id]
    (if-let [error (validate (get-captured-reference id) validate-no-related-article)]
      error
      (do
        (db/delete-captured-reference! {:id id})
        nil))))

(defn parse-retrieved-captured-reference
  [x]
  (into {} (map (fn [[k v]]
                  (case k
                    :classified [k (utils/int-to-bool v)]
                    :created_at [:created-at (utils/str->date v)]
                    [k v]))
                x)))

(def CAPTURED_REFERENCE_ID_ERR_NIL "Captured reference id can not be nil.")
(def CAPTURED_REFERENCE_ID_ERR_NOT_FOUND
  #(format "Captured reference with id %s was not found." %))

(def captured-reference-id-exists?
  (comp :res db/captured-reference-id-exists? (partial assoc {} :id)))

;; !!!! TODO -> use kti.validate
(defn validate-captured-reference-id [x]
  (cond
    (nil? x)
    CAPTURED_REFERENCE_ID_ERR_NIL

    (not (captured-reference-id-exists? x))
    (CAPTURED_REFERENCE_ID_ERR_NOT_FOUND x)))
