(ns kti.test.routes.services.reviews-test
  (:require [clojure.test :refer :all]
            [kti.routes.services.reviews :refer :all]
            [kti.test.helpers :refer :all]
            [kti.routes.services.articles :refer [create-article!]]
            [kti.routes.services.articles :refer [article-exists?]]
            [kti.db.core :refer [*db*] :as db]
            [clojure.java.jdbc :refer [insert!]]))


(use-fixtures :once fixture-start-env-and-db)
(use-fixtures :each fixture-bind-db-to-rollback-transaction)

(deftest test-get-review
  (testing "Base"
    (let [data {:id 999
                :id_article 888
                :feedback_text "Text"
                :status "COMPLETED"}]
      (insert! *db* :reviews data)
      (let [resp (get-review (:id data))]
        (are [k v] (= (k resp) v)
          :id (:id data)
          :id-article (:id_article data)
          :feedback-text (:feedback_text data)
          :status :completed))))
  (testing "missing"
    (is (nil? (get-review 21397218397)))))

(deftest test-parse-review
  (let [data {:id_article 12
              :feedback_text "Some text"
              :status "PENDING"}
        resp (parse-review data)]

    (testing "id-article"
      (is (= (:id-article resp) (:id_article data))))

    (testing "feedback_text"
      (is (= (:feedback-text resp) (:feedback_text data))))

    (testing "status"
      (is (= (:status resp) :pending))
      (is (= (:status (-> data (assoc :status "COMPLETED") parse-review))
             :completed))
      (is (= (:status (-> data (assoc :status "DISCARDED") parse-review))
             :discarded)))))

(deftest test-create-review!
  (let [captured-ref-id (create-test-captured-reference!)
        article-id (create-article! (get-article-data
                                     {:id-captured-reference captured-ref-id}))]
    (testing "Base"
      (let [data (get-review-data {:id-article article-id})
            id (create-review! data)
            review (get-review id)]
        (is (= review (assoc data :id id)))))
    (testing "Error if invalid status"
      (let [data (get-review-data {:id-article article-id :status :invalid})]
        (is (thrown? AssertionError (create-review! data)))))
    (testing "Error is article does not exists"
      (with-redefs [article-exists? (fn [_] false)]
        (is (thrown? clojure.lang.ExceptionInfo
                     (create-review! (get-review-data))))))))
