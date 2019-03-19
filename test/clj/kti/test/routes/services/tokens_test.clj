(ns kti.test.routes.services.tokens-test
  (:require [clojure.test :refer :all]
            [clojure.java.jdbc :as jdbc]
            [kti.db.core :as db :refer [*db*]]
            [kti.test.helpers :refer :all]
            [kti.routes.services.tokens :refer :all]
            [kti.routes.services.users.base :refer [get-user-by-email]]))

(use-fixtures :once fixture-start-app-and-env)
(use-fixtures :each fixture-bind-db-to-rollback-transaction)

(deftest test-get-all-tokens
  (let [tokens ["123" "456"]]
    (jdbc/execute! *db* ["DELETE FROM tokens"])
    (jdbc/insert-multi!
     *db*
     :tokens (map (fn [[x y]] (hash-map :id_user x :value y))
                  (map vector [1 2] tokens)))
    (is (= (get-all-token-values) tokens))))

(deftest test-gen-token
  (is (= (count (gen-token)) TOKEN-LENGTH))
  (is (every? TOKEN-CHARS (gen-token))))

(deftest test-get-current-token-for-email
  (testing "No user with this email"
    (let [email "aasdshja@nnsja.c"]
      (jdbc/delete! *db* :users ["email = ?" email])
      (is (nil? (get-current-token-for-email email)))))
  (testing "User exists, no token"
    (let [email "aasd@basd.cm"]
      (jdbc/insert! *db* :users {:email email})
      (is (nil? (get-current-token-for-email email)))))
  (testing "User and token exists"
    (let [value "1234567890"
          email "aa@217.b"
          user-id (-> (jdbc/insert! *db* :users  {:email email})
                      first
                      ((keyword "last_insert_rowid()")))]
      (jdbc/insert! *db* :tokens {:id_user user-id :value value})
      (is (= value (get-current-token-for-email email))))))

(deftest test-give-token!
  (testing "Creates user if not exists"
    (let [email "a@b.com"]
      (jdbc/delete! *db* :users ["email = ?" email])
      (give-token! email)
      (is ((comp not nil?) (get-user-by-email email)))))
  (testing "Deletes other tokens for same user"
    (let [email "b@c.d"]
      (jdbc/insert! *db* :users {:id 999 :email email})
      (jdbc/insert! *db* :tokens {:id_user 999 :value 123})
      (give-token! email)
      (is (= 0 ((comp :c first)
                (jdbc/query
                 *db*
                 ["SELECT COUNT(*) as c FROM tokens WHERE value = ?" 123]))))))
  (testing "Returns new token value"
    (with-redefs [gen-token (constantly 123)]
      (is (= (give-token! "a@b.com") 123))))
  (testing "Calls send-token-by-email"
    (let [send-token-by-email!-args (atom [])]
      (with-redefs [gen-token
                    (constantly 12)
                    send-token-by-email!
                    #(swap! send-token-by-email!-args conj %&)]
        (give-token! "a@b.com")
        (is (= [["a@b.com" 12]] @send-token-by-email!-args)))))
  (testing "Creates token"
    (let [create-token!-args (atom [])
          user {:id 1 :email "a@b.c"}
          value 222]
      (with-redefs [gen-token (constantly value)
                    get-user-by-email (constantly {:id 1 :email "a@b.c"})
                    create-token! #(swap! create-token!-args conj %&)]
        (give-token! (:email user))
        (is (= [[{:user user :value value}]] @create-token!-args))))))

(deftest test-create-token!
  (let [value 222 user {:id 22 :email "v@g.c"}]
    (jdbc/delete! *db* :tokens ["value = ?" value])
    (create-token! {:user user :value value})
    (is (= 1 ((comp :c first)
              (jdbc/query
               *db*
               [(str "SELECT COUNT(*) AS c FROM tokens "
                     "WHERE value = ? AND id_user = ?")
                value (:id user)]))))))
