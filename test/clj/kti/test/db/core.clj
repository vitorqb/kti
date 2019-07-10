(ns kti.test.db.core
  (:require [kti.db.state :refer [*db*]]
            [kti.db.core :as db]
            [kti.db.user :as db.user]
            [luminus-migrations.core :as migrations]
            [clojure.test :refer :all]
            [clojure.java.jdbc :as jdbc]
            [kti.config :refer [env]]
            [mount.core :as mount]))

(use-fixtures
  :once
  (fn [f]
    (mount/start
      #'kti.config/env
      #'kti.db.state/*db*)
    (migrations/migrate ["migrate"] (select-keys env [:database-url]))
    (f)))

(deftest test-users
  (jdbc/with-db-transaction [t-conn *db*]
    (jdbc/db-set-rollback-only! t-conn)
    (is (= 1 ((keyword "last_insert_rowid()")
              (db.user/create-user!
               t-conn
               {:id         "1"
                :email      "sam.smith@example.com"}))))
    (is (= {:id 1 :email "sam.smith@example.com"}
           (db.user/get-user t-conn {:id "1"})))))


(deftest test-calculate-offset
  (is (= (db/calculate-offset {:page 4 :page-size 3}) 9)))

(deftest test-paginate-opts->sqlvec
  (let [page 3 page-size 4 paginate-opts {:page page :page-size page-size}]
    (is (= (db/paginate-opts->sqlvec paginate-opts) ["LIMIT ? OFFSET ?" 4 8]))))
