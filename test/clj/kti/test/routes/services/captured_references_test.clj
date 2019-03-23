(ns kti.test.routes.services.captured-references-test
  (:require [java-time]
            [kti.test.helpers :refer :all]
            [kti.utils :as utils]
            [luminus-migrations.core :as migrations]
            [kti.routes.services.captured-references :refer :all]
            [kti.routes.services.captured-references.base
             :refer [parse-retrieved-captured-reference get-captured-reference]]
            [kti.routes.services.articles :refer [get-article]]
            [kti.routes.services.users :refer [get-user get-user-for]]
            [kti.db.core :refer [*db*] :as db]
            [kti.config :refer [env]]
            [kti.validation :refer [->KtiError]]
            [clojure.test :refer :all]
            [clojure.java.jdbc :as jdbc]
            [mount.core :as mount]))

(use-fixtures :once fixture-start-env-and-db)
(use-fixtures :each fixture-bind-db-to-rollback-transaction)

(deftest test-creating-captured-references
  (testing "Creating and returning a new captured reference"
    (db/delete-all-captured-references)
    (db/delete-all-articles)
    (let [data (get-captured-reference-data {:id-user 123})
          id (create-captured-reference! data)
          retrieved-reference (get-captured-reference id)]
      (is (integer? id))
      (are [k v] (= (k retrieved-reference) v)
        :id id
        :reference (:reference data)
        :classified false
        :created-at (:created-at data))
      (is (= (:id-user data) 123))))

  (testing "Uses utils/now when no created-at"
    (let [date (java-time/local-date-time 2017 4 5 1 1)]
      (with-redefs [utils/now (constantly date)]
        (is (= (-> {:reference "abc"}
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
          :reference reference-str))))

  (testing "Validates min length"
    (with-redefs [validate-captured-ref-reference-min-length (fn [&_] "foo")]
      (is (= (->KtiError "foo") (create-captured-reference! {:reference "bar"}))))))

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

(deftest test-get-user-captured-references
  (let [user (get-user (create-test-user!))]
    (testing "Empty" (is (= [] (get-user-captured-references user))))
      (let [cap-ref1 (get-captured-reference
                      (create-test-captured-reference! {:user user}))
            cap-ref2 (get-captured-reference
                      (create-test-captured-reference! {:user user}))]
        (testing "See his own"
          (is (= [cap-ref1 cap-ref2] (get-user-captured-references user))))
        (testing "Don't see other user's"
          (is (= [] (get-user-captured-references
                     (get-user (create-test-user!)))))))))

(deftest test-get-captured-reference
  (db/delete-all-articles)
  (db/delete-all-captured-references)
  (testing "When id does not exist"
    (is (= (get-captured-reference 921928129) nil)))
  (testing "Classified is True after an article is created"
    (let [data (-> (create-test-captured-reference!) get-captured-reference)]
      (is (false? (:classified data)))
      (create-test-article! :id-captured-reference (:id data))
      (is (true? (-> data :id get-captured-reference :classified)))))
  (testing "Nil when belongs to other user"
    (let [user (get-user (create-test-user!))
          {:keys [id] :as cap-ref}
          (get-captured-reference (create-test-captured-reference! {:user user}))
          other-user (get-user (create-test-user!))]
      (is (nil? (get-captured-reference id other-user)))
      (is (= cap-ref (get-captured-reference id user))))))

(deftest test-update-captured-reference!
  (testing "Updating reference"
    (let [id (create-captured-reference! (get-captured-reference-data))
          original-captured-reference (get-captured-reference id)
          new-reference "new reference!"]
      (is (= (update-captured-reference! id {:reference new-reference})
             nil))
      (is (= (get-captured-reference id)
             (assoc original-captured-reference :reference new-reference)))))
  (testing "Validates with min length"
    (with-redefs [validate-captured-ref-reference-min-length (fn [&_] "foo")]
      (is (= (update-captured-reference! (create-test-captured-reference!) {})
             (->KtiError "foo"))))))

(deftest test-validate-no-related-article
  (let [captured-ref (get-captured-reference (create-test-captured-reference!))]
    (is (nil? (validate-no-related-article captured-ref)))
    (create-test-article! :id-captured-reference (:id captured-ref))
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

(deftest test-validate-captured-ref-reference-min-length
  (is (= ERR-MSG-REFERENCE-MIN-LENGTH
         (validate-captured-ref-reference-min-length {:reference ""})))
  (is (= ERR-MSG-REFERENCE-MIN-LENGTH
         (validate-captured-ref-reference-min-length {:reference "a"})))
  (is (nil? (validate-captured-ref-reference-min-length {:reference "aa"}))))
