(ns kti.routes.services.captured-references.base
  (:require [kti.db.core :as db :refer [*db*]]
            [kti.utils :as utils]))

(defn parse-retrieved-captured-reference
  [x]
  (into {} (map (fn [[k v]]
                  (case k
                    :classified [k (utils/int-to-bool v)]
                    :created_at [:created-at (utils/str->date v)]
                    [k v]))
                x)))

(defn get-captured-reference
  ([id] (get-captured-reference *db* id))

  ([db-con id]
   (some-> (db/get-captured-reference db-con {:id id})
           (parse-retrieved-captured-reference))))
