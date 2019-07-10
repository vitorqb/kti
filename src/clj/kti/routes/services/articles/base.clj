(ns kti.routes.services.articles.base
  (:require [clojure.string :as string]
            [kti.db.articles :as db.articles]))

(defn parse-article-data [x]
  (-> x
      (select-keys [:id :description :tags])
      (assoc :action-link (:action_link x))
      (assoc :id-captured-reference (:id_captured_reference x))
      (update :tags #(and % (set (string/split % #" "))))
      (update :tags #(or % #{}))))

(defn get-article-for-captured-reference
  [x]
  (some-> x db.articles/get-article-for-captured-reference parse-article-data))

(defn get-article
  ([id] (get-article id nil))
  ([id user] (some-> {:id id :user user} db.articles/get-article parse-article-data)))
