(ns kti.test.helpers
  (:require [ring.mock.request :refer :all]
            [luminus-migrations.core :as migrations]
            [cheshire.core :as cheshire]
            [kti.handler]
            [kti.db.core :as db]
            [kti.utils :as utils :refer [set-default set-default-fn]]
            [kti.config :refer [env]]
            [kti.db.core :refer [*db*]]
            [kti.routes.services.captured-references :as service-captured-references]
            [kti.routes.services.articles :refer [create-article!]]
            [kti.routes.services.users.base :refer [create-user!]]
            [kti.routes.services.users :refer [get-user]]
            [kti.routes.services.tokens :refer [gen-token create-token!]]
            [kti.routes.services.reviews :refer [create-review!]]
            [mount.core :as mount]
            [clojure.java.jdbc :as jdbc]))

(declare create-test-user!)

;; Misc
(defn body->map
  "Converts a response body into a map. Assumes it is json."
  [json-body]
  (cheshire/parse-string (slurp json-body) true))
(defn not-found? [response] (= 404 (:status response)))
(defn ok? [response] (= 200 (:status response)))
(defn empty-response? [r] (and (ok? r) (= [] (body->map (:body r)))))
(defn missing-auth? [r] (and (= 400 (:status r))
                             (= {:authorization "missing-required-key"}
                                (-> r :body body->map :errors))))
(defn auth-header [request token]
  (header request :authorization (str "TOKEN " token)))

;; Db populate and cleanup
(def email-chars "1234567890qwertyuiopasdfghjklzxcvbnm")
(def user-emails (atom (for [x email-chars y email-chars z email-chars]
                         (str x "@" y "." z))))
(defn get-user-email []
  (let [out (first @user-emails)]
    (swap! user-emails rest)
    out))
  
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
              (set-default-fn :user    create-test-user!)
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
  (let [raw-data (apply hash-map key-val)]
    (assert (not (every? raw-data #{:id-captured-reference :user})))
    (let [data (if-let [user (:user raw-data)]
                 (assoc raw-data
                        :id-captured-reference
                        (create-test-captured-reference! {:user user}))
                 raw-data)]
      (let [keys [:id-captured-reference :description :action-link :tags]
            get-default #(case %
                           :id-captured-reference (create-test-captured-reference!)
                           :description "Search for git book"
                           :action-link "https://www.google.com/search?q=git+book"
                           :tags #{"google"})
            {:keys [error-msg] :as id}
            (create-article!
             (into {} (map (fn [k] [k (or (k data) (get-default k))]) keys)))]
        (assert (nil? error-msg) (str "Failed to create article for test: " error-msg))
        id))))

(defn create-test-user! [& key-val]
  (let [data (apply hash-map key-val)
        keys [:email]
        get-default #(case % :email (get-user-email))]
    (create-user! (into {} (map #(vector % (or (% data) (get-default %)))) keys))))

(defn create-test-token!
  ([] (create-test-token! (get-user (create-test-user!))))
  ([user] (create-token! {:user user :value (gen-token)})))
  

(defn get-captured-reference-data
  "Captured-reference data for testing"
  ([] (get-captured-reference-data {}))
  ([data] (-> data
              (set-default    :reference  "Some reference")
              (set-default    :created-at (java-time/local-date-time 1993 11 23))
              (set-default-fn :user       (comp get-user create-test-user!)))))

(defn get-review-data
  ([] (get-review-data {}))
  ([data] (-> data
              (set-default :id-article 92179)
              (set-default :feedback-text "Feedback text...")
              (set-default :status :in-progress))))

(defn create-test-review! [& key-vals]
  (let [raw-data (apply hash-map key-vals)]
    (assert (not (and (raw-data :user) (raw-data :id-article))))
    (let [data (if-let [user (:user raw-data)]
                 (assoc raw-data :id-article (create-test-article! :user user))
                 raw-data)
          keys [:id-article :feedback-text :status]
          get-default (fn [k] (case k
                                :id-article (create-test-article!)
                                :feedback-text "Feed my back text"
                                :status :in-progress))
          {:keys [error-msg] :as id}
          (create-review!
           (into {} (map (fn [k] [k (or (k data) (get-default k))]) keys)))]
      (assert (nil? error-msg) (str "Failed to create review for test: " error-msg))
      id)))

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
