(ns kti.test.routes.services.captured-references-test
  (:require [java-time]
            [kti.test.helpers :refer :all]
            [kti.utils :as utils]
            [luminus-migrations.core :as migrations]
            [kti.routes.services.captured-references :refer :all]
            [kti.routes.services.captured-references.base
             :refer [parse-retrieved-captured-reference get-captured-reference]]
            [kti.routes.services.articles :refer [create-article! get-article]]
            [kti.db.core :refer [*db*] :as db]
            [kti.config :refer [env]]
            [clojure.test :refer :all]
            [clojure.java.jdbc :as jdbc]
            [mount.core :as mount]))

(use-fixtures :once fixture-start-env-and-db)
(use-fixtures :each fixture-bind-db-to-rollback-transaction)

(deftest test-creating-captured-references
  (testing "Creating and returning a new captured reference"
    (db/delete-all-captured-references)
    (db/delete-all-articles)
    (let [data (get-captured-reference-data)
          id (create-captured-reference! data)
          retrieved-reference (get-captured-reference id)]
      (is (integer? id))
      (are [k v] (= (k retrieved-reference) v)
        :id id
        :reference (:reference data)
        :classified false
        :created-at (:created-at data))))

  (testing "Uses utils/now when no created-at"
    (let [date (java-time/local-date-time 2017 4 5 1 1)]
      (with-redefs [utils/now (constantly date)]
        (is (= (-> {:reference ""}
                   create-captured-reference!
                   get-captured-reference
                   :created-at)
               date)))))

  (testing "Creating with specific datetime"
    (db/delete-all-captured-references)
    (let [reference "another ref"
          datetime (java-time/local-date-time 2018 11 23 12 12)]
      (is (= (create-captured-reference! {:reference reference
                                          :created-at datetime})
             1))
      (let [retrieved-reference (get-captured-reference 1)]
        (is (= (:id retrieved-reference) 1))
        (is (= (:reference retrieved-reference) reference))
        (is (= (:classified retrieved-reference) false))
        (is (= (:created-at retrieved-reference) datetime)))))

  (testing "Creating multiple"
    (db/delete-all-captured-references)
    (doseq [[reference-str id] [["one" 1] ["two" 2]]]
      (is (= (create-captured-reference!
              (get-captured-reference-data {:reference reference-str}))
             id))
      (let [retrieved-reference (get-captured-reference id)]
        (are [k v] (= (k retrieved-reference) v)
          :id id
          :reference reference-str)))))

(deftest test-parse-retrieved-captured-reference
  (let [retrieved
        {:id 12
         :reference "reference"
         :created_at (utils/date->str
                      (java-time/local-date-time 2018 1 1 12 22 10))
         :classified 0}
        parsed (parse-retrieved-captured-reference retrieved)]

    (testing "renames created_at -> created-at"
      (is (contains? parsed :created-at))
      (is (not (contains? parsed :created_at))))

    (testing "parses date"
      (is (= (:created-at parsed)
             (utils/str->date (:created_at retrieved)))))

    (testing "transforms classified into bool"
      (is (= (:classified parsed)
             false)))))

(deftest test-get-all-captured-references
  (db/delete-all-captured-references)
  (let [references ["ref one" "ref two"]
        datetime-str "2018-01-01T00:00:00"]
    (doseq [reference references]
      (db/create-captured-reference! {:reference reference
                                      :created-at datetime-str}))
    (let [all-captured-references (get-all-captured-references)]
      (is (= (count all-captured-references) (count references)))
      (is (= (set (map :reference all-captured-references))
             (set references)))
      (is (= (set (map :created-at all-captured-references))
             #{(utils/str->date datetime-str)})))))

(deftest test-get-captured-reference
  (db/delete-all-articles)
  (db/delete-all-captured-references)
  (testing "When id does not exist"
    (is (= (get-captured-reference 921928129) nil)))
  (testing "Classified is True after an article is created"
    (let [data (-> (create-test-captured-reference!) get-captured-reference)]
      (is (false? (:classified data)))
      (create-article! (get-article-data {:id-captured-reference (:id data)}))
      (is (true? (-> data :id get-captured-reference :classified))))))

(deftest test-update-captured-reference!
  (testing "Empty map"
    (let [id (create-captured-reference! (get-captured-reference-data))
          original-captured-reference (get-captured-reference id)]
      (update-captured-reference! id {})
      (is (= (get-captured-reference id) original-captured-reference))))
  (testing "Updating reference"
    (let [id (create-captured-reference! (get-captured-reference-data))
          original-captured-reference (get-captured-reference id)
          new-reference "new reference!"]
      (update-captured-reference! id {:reference new-reference})
      (is (= (get-captured-reference id)
             (assoc original-captured-reference :reference new-reference))))))

(deftest test-validate-captured-reference-id
  (is (= (validate-captured-reference-id nil) CAPTURED_REFERENCE_ID_ERR_NIL))
  (is (= (validate-captured-reference-id "a")
         (CAPTURED_REFERENCE_ID_ERR_NOT_FOUND "a")))
  (let [id (create-test-captured-reference!)]
    (is (nil? (validate-captured-reference-id id)))))

(deftest test-captured-reference-id-exists?
  (is (nil? (captured-reference-id-exists? 291372189731)))
  (let [id (create-test-captured-reference!)]
    (is (= (captured-reference-id-exists? id) id))))

(deftest test-validate-no-related-article
  (let [captured-ref (get-captured-reference (create-test-captured-reference!))]
   ;; !!!! TODO -> Change to call-validation
    (is (nil? (validate-no-related-article captured-ref)))
    (create-article! (get-article-data {:id-captured-reference (:id captured-ref)}))
    (is (= DELETE-ERR-MSG-ARTICLE-EXISTS
           (validate-no-related-article captured-ref)))))

(deftest test-delete-captured-reference!
  (testing "Base"
    (let [id (create-test-captured-reference!)]
      (delete-captured-reference! id)
      (is (nil? (get-captured-reference id)))))

  (testing "Validates with no-related-article"
    (with-redefs [validate-no-related-article (constantly "foobar")]
      (let [{:keys [error-msg]} (delete-captured-reference! 1)]
        (is (= error-msg "foobar"))))))
