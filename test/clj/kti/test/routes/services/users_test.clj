(ns kti.test.routes.services.users-test
  (:require [clojure.test :refer :all]
            [clojure.java.jdbc :as jdbc]
            [kti.db.core :as db :refer [*db*]]
            [kti.test.helpers :refer :all]
            [kti.routes.services.users :refer :all]
            [kti.routes.services.users.base :refer :all]
            [kti.routes.services.articles.base :refer [get-article]]
            [kti.routes.services.reviews :refer [get-review]]
            [kti.routes.services.captured-references.base
             :refer [get-captured-reference]]))

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

(deftest test-get-user-from-token
  (is (= (get-user-from-token nil) nil))
  (is (= (get-user-from-token {}) nil))
  (let [token "456"]
    (jdbc/delete! *db* :tokens ["VALUE = ?" token])
    (is (= (get-user-from-token token) nil)))
  (let [token "789" user {:id 123 :email "a@b.com"}]
    (jdbc/delete! *db* :tokens ["VALUE = ?" token])
    (jdbc/insert! *db* :users user)
    (jdbc/insert! *db* :tokens {:id_user (:id user) :value token})
    (is (= (get-user-from-token token) user))))

(deftest test-get-user
  (jdbc/delete! *db* :users [])
  (is (nil? (get-user 1)))
  (let [user {:id 1 :email "a@b.com"}]
    (jdbc/insert! *db* :users user)
    (is (= user (get-user 1)))))

(deftest test-get-user-for
  (testing "captured-reference"
    (let [user (get-user (create-test-user!))
          cap-ref (get-captured-reference
                   (create-test-captured-reference! {:user user}))]
      (is (= user (get-user-for :captured-reference cap-ref)))))
  (testing "article"
    (let [user (get-user (create-test-user!))
          article (get-article (create-test-article! :user user))]
      (is (= user (get-user-for :article article)))))
  (testing "review"
    (let [user (get-user (create-test-user!))
          review (get-review (create-test-review! :user user))]
      (is (= user (get-user-for :review review))))))
