(ns kti.test.routes.services.users-test
  (:require [clojure.test :refer :all]
            [clojure.java.jdbc :as jdbc]
            [kti.db.core :as db :refer [*db*]]
            [kti.test.helpers :refer :all]
            [kti.routes.services.users :refer :all]
            [kti.routes.services.users.base :refer :all]))

(use-fixtures :once fixture-start-app-and-env)
(use-fixtures :each fixture-bind-db-to-rollback-transaction)

(deftest test-get-user-by-email
  (let [email "aaa@bbb.ccc"]
    (jdbc/delete! *db* :users ["email = ?" email])
    (is (nil? (get-user-by-email email)))
    (jdbc/insert! *db* :users {:email email})
    (is (= email (:email (get-user-by-email email))))))

(deftest test-create-user
  (let [email "a@a.a"]
    (jdbc/delete! *db* :users ["email = ?" email])
    (let [id (create-user! {:email email})]
      (is (= 1 ((comp :c first)
                (jdbc/query
                 *db*
                 [(str "SELECT COUNT(*) as c from users "
                       "where email = ? AND id = ?")
                  email id])))))))
