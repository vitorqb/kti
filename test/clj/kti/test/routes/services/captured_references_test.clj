(ns kti.test.routes.services.captured-references-test
  (:require [java-time]
            [kti.utils :as utils]
            [luminus-migrations.core :as migrations]
            [kti.routes.services.captured-references :refer :all]
            [kti.db.core :refer [*db*] :as db]
            [kti.config :refer [env]]
            [clojure.test :refer :all]
            [clojure.java.jdbc :as jdbc]
            [mount.core :as mount]))

(def captured-reference-data {:reference "Some reference"
                              :created-at (java-time/local-date-time 1993 11 23)})

(use-fixtures
  :once
  (fn [f]
    (mount/start
     #'kti.config/env
     #'kti.db.core/*db*)
    (migrations/migrate ["migrate"] (select-keys env [:database-url]))
    (db/delete-all-captured-references *db*)
    (f)))

(deftest test-creating-captured-references
  (testing "Creating and returning a new captured reference"
    (jdbc/with-db-transaction [t-conn *db*]
      (jdbc/db-set-rollback-only! t-conn)
      (let [reference "some-ref"
            id (create-captured-reference! t-conn {:reference reference})
            retrieved-reference (get-captured-reference t-conn id)]
        (is (integer? id))
        (is (= (:id retrieved-reference) id))
        (is (= (:reference retrieved-reference) reference))
        (is (= (:classified retrieved-reference) false))
        (is (-> (:created-at retrieved-reference)
                (java-time/time-between (java-time/local-date-time) :seconds))
            (< 3)))))

  (testing "Creating with specific datetime"
    (jdbc/with-db-transaction [t-conn *db*]
      (jdbc/db-set-rollback-only! t-conn)
      (let [reference "another ref"
            datetime (java-time/local-date-time 2018 11 23 12 12)]
        (is (= (create-captured-reference! t-conn {:reference reference
                                                   :created-at datetime})
               1))
        (let [retrieved-reference (get-captured-reference t-conn 1)]
          (is (= (:id retrieved-reference) 1))
          (is (= (:reference retrieved-reference) reference))
          (is (= (:classified retrieved-reference) false))
            (is (= (:created-at retrieved-reference) datetime))))))

  (testing "Creating multiple"
    (jdbc/with-db-transaction [t-conn *db*]
      (jdbc/db-set-rollback-only! t-conn)
      (doseq [[reference-str id] [["one" 1]
                                  ["two" 2]]]
        (is (= (create-captured-reference! t-conn {:reference reference-str})
               id))
        (let [retrieved-reference (get-captured-reference t-conn id)]
          (is (= (:id retrieved-reference) id))
          (is (= (:reference retrieved-reference) reference-str)))))))


(deftest test-parse-retrieved-captured-reference
  (let [retrieved
        {:id 12
         :reference "reference"
         :created_at (java-time/format
                      (java-time/local-date-time 2018 1 1 12 22 10))
         :classified 0}
        parsed (parse-retrieved-captured-reference retrieved)]
    (testing "renames created_at -> created-at"
      (is (contains? parsed :created-at))
      (is (not (contains? parsed :created_at))))
    (testing "parses date"
      (is (= (:created-at parsed)
             (java-time/local-date-time (:created_at retrieved)))))
    (testing "transforms classified into bool"
      (is (= (:classified parsed)
             false)))))

(deftest test-get-all-captured-references
  (jdbc/with-db-transaction [t-conn *db*]
    (jdbc/db-set-rollback-only! t-conn)
    (db/delete-all-captured-references t-conn)
    (let [references ["ref one" "ref two"]
          datetime-str "2018-01-01T00:00:00"]
      (doseq [reference references]
        (db/create-captured-reference! t-conn {:reference reference
                                               :created-at datetime-str}))
      (let [all-captured-references (get-all-captured-references t-conn)]
        (is (= (count all-captured-references) (count references)))
        (is (= (set (map :reference all-captured-references))
               (set references)))
        (is (= (set (map :created-at all-captured-references))
               #{(utils/str->date datetime-str)}))))))

(deftest test-get-captured-reference
  (jdbc/with-db-transaction [t-conn *db*]
    (jdbc/db-set-rollback-only! t-conn)
    (testing "When id does not exist"
      (is (= (get-captured-reference 921928129) nil)))))

(deftest test-update-captured-reference!
  (jdbc/with-db-transaction [t-conn *db*]
    (jdbc/db-set-rollback-only! t-conn)
    (testing "Empty map"
      (let [id (create-captured-reference! t-conn captured-reference-data)
            original-captured-reference (get-captured-reference t-conn id)]
        (update-captured-reference! t-conn id {})
        (is (= (get-captured-reference t-conn id) original-captured-reference))))
    (testing "Updating reference"
      (let [id (create-captured-reference! t-conn captured-reference-data)
            original-captured-reference (get-captured-reference t-conn id)
            new-reference "new reference!"]
        (update-captured-reference! t-conn id {:reference new-reference})
        (is (= (get-captured-reference t-conn id)
               (assoc original-captured-reference :reference new-reference)))))))

