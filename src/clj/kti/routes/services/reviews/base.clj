(ns kti.routes.services.reviews.base
  (:require [clojure.string :as str]
            [kti.utils :refer [string->status]]
            [kti.db.core :as db :refer [*db*]]))

(def status->string (comp str/upper-case name))

(defn parse-review
  "Parses raw data for a review, retrieved from the db"
  [x]
  (into {} (map (fn [[k v]]
                  (case k
                    :id_article    [:id-article v]
                    :feedback_text [:feedback-text v]
                    :status        [k (string->status v)]
                    [k v])))
        x))

(defn get-review-for-article [x]
  (some-> x db/get-review-for-article parse-review))
