(ns kti.db.articles
  (:require
   [kti.db.state :refer [*db*]]
   [conman.core :as conman]))

(conman/bind-connection *db* "sql/articles.sql")

(defn get-article
  ([params] (q-get-article (assoc params :select (snip-select-article))))
  ([db params & rest]
   (apply q-get-article db (assoc params :select (snip-select-article) rest))))

(defn get-user-articles [params]
  (q-get-user-articles (assoc params :select (snip-select-article))))

(defn get-article-for-captured-reference [params]
  (q-get-article-for-captured-reference
   (assoc params :select (snip-select-article))))
