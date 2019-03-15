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
              :status "IN-PROGRESS"}
        resp (parse-review data)]

    (testing "id-article"
      (is (= (:id-article resp) (:id_article data))))

    (testing "feedback_text"
      (is (= (:feedback-text resp) (:feedback_text data))))

    (testing "status"
      (is (= (:status resp) :in-progress))
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

    (testing "Uses validate-review-status"
      (let [args (atom nil) data (get-review-data {:id-article article-id})]
        (with-redefs [validate-review-status #(reset! args %&)]
          (create-review! data)
          (is (= @args (list (:status data)))))))

    (testing "Uses validate-article"
      (let [args (atom nil) data (get-review-data {:id-article article-id})]
        (with-redefs [validate-id-article #(reset! args %&)]
          (create-review! data)
          (is (= @args (list article-id))))))

    (testing "Error is article does not exists"
      (with-redefs [article-exists? (fn [_] false)]
        (is (thrown? clojure.lang.ExceptionInfo
                     (create-review! (get-review-data))))))))

(deftest test-get-all-reviews
  (db/delete-all-reviews)
  (let [captured-refs-ids [(create-test-captured-reference!)
                           (create-test-captured-reference!)]
        articles-ids (doall
                      (map (fn [id]
                             (create-article! (get-article-data
                                               {:id-captured-reference id})))
                           captured-refs-ids))
        reviews-ids (doall (map (fn [id]
                                  (create-review! (get-review-data
                                                   {:id-article id})))
                          articles-ids))]
    (is (= (get-all-reviews) (map get-review reviews-ids)))))


(deftest test-validate-review-status
  (is (thrown? AssertionError (validate-review-status :not-a-valid-status)))
  (doseq [s review-status] (is (nil? (validate-review-status s)))))

(deftest test-validate-id-article
  (is (thrown-with-msg?
       clojure.lang.ExceptionInfo
       #"Article with id .+ does not exists"
       (validate-id-article "not-a-valid-id")))
  (let [captured-ref-id (create-test-captured-reference!)
        article-id (create-article! (get-article-data
                                     {:id-captured-reference captured-ref-id}))]
    (is (nil? (validate-id-article article-id)))))

(deftest test-update-review!
  (let [captured-ref-id (create-test-captured-reference!)
        article-id (create-article! (get-article-data
                                     {:id-captured-ref captured-ref-id}))
        review-id (create-review! (get-review-data {:id-article article-id}))
        new-captured-ref-id (create-test-captured-reference!)
        new-article-id (create-article! (get-article-data
                                         {:id-captured-ref new-captured-ref-id}))
        new-data {:id-article new-article-id
                  :feedback-text "NNNewwww feedback"
                  :status :completed}
        validate-id-article-args (atom nil)
        validate-review-status-args (atom nil)]
    (with-redefs [validate-id-article #(reset! validate-id-article-args %&)
                  validate-review-status #(reset! validate-review-status-args %&)]
      (update-review! review-id new-data)

      (testing "Uses validate-review-status"
        (is (= @validate-review-status-args (list (:status new-data)))))

      (testing "Uses validate-id-article"
        (is (= @validate-id-article-args (list (:id-article new-data)))))

      (testing "Updates values in the db"
        (is (= (get-review review-id)
               (assoc new-data :id review-id)))))))

(deftest test-delete-review!
  (let [id (->> (create-test-captured-reference!)
                (hash-map :id-captured-reference)
                get-article-data
                create-article!
                (hash-map :id-article)
                get-review-data
                create-review!)]
    (is (not (nil? (get-review id))))
    (delete-review! id)
    (is (nil? (get-review id)))))
