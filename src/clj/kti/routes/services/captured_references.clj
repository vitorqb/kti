(ns kti.routes.services.captured-references
  (:require [kti.db.core :as db :refer [*db*]]
            [java-time]
            [kti.utils :as utils]))

(declare parse-retrieved-captured-reference)

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

(defn validate-captured-reference-id [x]
  (cond
    (nil? x)
    CAPTURED_REFERENCE_ID_ERR_NIL

    (not (captured-reference-id-exists? x))
    (CAPTURED_REFERENCE_ID_ERR_NOT_FOUND x)))
