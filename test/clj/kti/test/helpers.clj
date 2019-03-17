(ns kti.test.helpers
  (:require [ring.mock.request :refer :all]
            [luminus-migrations.core :as migrations]
            [cheshire.core :as cheshire]
            [kti.handler]
            [kti.db.core :as db]
            [kti.utils :as utils :refer [set-default]]
            [kti.config :refer [env]]
            [kti.db.core :refer [*db*]]
            [kti.routes.services.captured-references :as service-captured-references]
            [kti.routes.services.articles :refer [create-article!]]
            [mount.core :as mount]
            [clojure.java.jdbc :as jdbc]))

;; Misc
(defn body->map
  "Converts a response body into a map. Assumes it is json."
  [json-body]
  (cheshire/parse-string (slurp json-body) true))
(defn not-found? [response] (= 404 (:status response)))
(defn ok? [response] (= 200 (:status response)))
(defn empty-response? [r] (and (ok? r) (= [] (body->map (:body r)))))

;; Db populate and cleanup
(defn clean-articles-and-tags []
  (db/delete-all-articles)
  (db/delete-all-articles-tags)
  (db/delete-all-tags))

(defn create-test-captured-reference!
  "Creates a reference for testing"
  ([] (create-test-captured-reference! {}))
  ([data] (-> data
              (set-default :reference  "A Reference")
              (set-default :created-at (utils/str->date "2018-01-01T12:12:23"))
              service-captured-references/create-captured-reference!)))

(defn get-article-data
  "Article data for testing"
  ([] (get-article-data {}))
  ([data] (-> data
              (set-default :id-captured-reference 1)
              (set-default :description "Search for git book.")
              (set-default :action-link "https://www.google.com/search?q=git+book")
              (set-default :tags #{"google"}))))

(defn create-test-article! [& key-val]
  (let [data (apply hash-map key-val)
        keys [:id-captured-reference :description :action-link :tags]
        get-default #(case %
                       :id-captured-reference (create-test-captured-reference!)
                       :description "Search for git book"
                       :action-link "https://www.google.com/search?q=git+book"
                       :tags #{"google"})
        {:keys [error-msg] :as id}
        (create-article!
         (into {} (map (fn [k] [k (or (k data) (get-default k))]) keys)))]
    (assert (nil? error-msg) (str "Failed to create article for test: " error-msg))
    id))

(defn get-captured-reference-data
  "Captured-reference data for testing"
  ([] (get-captured-reference-data {}))
  ([data] (-> data
              (set-default :reference "Some reference")
              (set-default :created-at (java-time/local-date-time 1993 11 23)))))

(defn get-review-data
  ([] (get-review-data {}))
  ([data] (-> data
              (set-default :id-article 92179)
              (set-default :feedback-text "Feedback text...")
              (set-default :status :in-progress))))


;; Fixtures
(defn fixture-start-app-and-env
  [f]
  (mount/start #'kti.config/env #'kti.handler/app)
  (f))

(defn fixture-start-env-and-db
  [f]
  (mount/start #'kti.config/env #'kti.db.core/*db*)
  (migrations/migrate ["migrate"] (select-keys env [:database-url]))
  (f))

(defn fixture-bind-db-to-rollback-transaction
  [f]
  (jdbc/with-db-transaction [t-conn *db*]
    (jdbc/db-set-rollback-only! t-conn)
    (binding [*db* t-conn]
      (f))))
