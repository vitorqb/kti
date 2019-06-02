(ns kti.test.routes.services.reviews-test
  (:require [clojure.test :refer :all]
            [kti.routes.services.reviews :refer :all]
            [kti.routes.services.reviews.base :refer :all]
            [kti.routes.services.users :refer [get-user]]
            [kti.validation :refer [->KtiError]]
            [kti.test.helpers :refer :all]
            [kti.routes.services.articles
             :refer [article-exists? create-article!]]
            [kti.routes.services.articles.base :refer [get-article]]
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
    (is (nil? (get-review 21397218397))))
  (testing "with user"
    (let [user (get-user (create-test-user!))
          id (create-test-review! :user user)]
      (is ((comp not nil?) (get-review id user)))
      (is (= (get-review id) (get-review id user)))
      (is (nil? (get-review (create-test-review!) user))))))

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
  (let [article-id (create-test-article!)]
    (testing "Base"
      (let [data (get-review-data {:id-article article-id})
            id (create-review! data)
            review (get-review id)]
        (is (= review (assoc data :id id)))))

    (testing "Error if invalid status"
      (let [data (get-review-data {:id-article article-id :status :invalid})]
        (is (= (->KtiError (INVALID-REVIEW-STATUS :invalid))
               (create-review! data)))))

    (testing "Uses validate-review-status"
      (let [args (atom nil) data (get-review-data {:id-article article-id})]
        (with-redefs [validate-review-status #(do (reset! args %&) "baz")]
          (is (= (->KtiError "baz") (create-review! data)))
          (is (= @args (list data))))))

    (testing "Uses validate-id-article"
      (let [args (atom nil) data (get-review-data {:id-article article-id})]
        (with-redefs [validate-id-article #(do (reset! args %&) "foo")]
          (is (= (->KtiError "foo") (create-review! data)))
          (is (= @args (list data))))))

    (testing "Uses validate-article-belongs-to-user"
      (let [user :user
            args (atom nil)
            data (get-review-data {:id-article (create-test-article!)})]
        (with-redefs [validate-article-belongs-to-user
                      #(do (reset! args %&) "bar")]
          (is (= (->KtiError "bar") (create-review! data user)))
          (is (= @args (list user data))))))

    (testing "Uses validate-unique-review-for-article"
      (let [id-article (create-test-article!)
            review-data (get-review-data {:id-article id-article})]
        (with-redefs [validate-unique-review-for-article
                      (fn [rd]
                        (is (= rd review-data))
                        ::val-result)]
          (is (= (->KtiError ::val-result) (create-review! review-data))))))

    (testing "Error if article does not exists"
      (let [data (get-review-data)]
        (with-redefs [article-exists? (fn [_] false)]
          (is (= (->KtiError (INVALID-ID-ARTICLE (:id-article data)))
                 (create-review! data))))))))

(deftest test-get-all-reviews
  (db/delete-all-reviews)
  (let [articles-ids (doall (map (fn [_] (create-test-article!))
                                 [1 2 3 4 5]))
        reviews-ids (doall (map (fn [id]
                                  (create-review! (get-review-data
                                                   {:id-article id})))
                          articles-ids))]
    (is (= (get-all-reviews) (map get-review reviews-ids)))))


(deftest test-validate-review-status
  (is (= (INVALID-REVIEW-STATUS :not-a-valid-status)
         (validate-review-status {:status :not-a-valid-status})))
  (doseq [s review-status] (is (nil? (validate-review-status {:status s})))))

(deftest test-validate-id-article
  (is (= "Article with id not-a-valid-id does not exists"
         (validate-id-article {:id-article "not-a-valid-id"})))
  (let [article-id (create-test-article!)]
    (is (nil? (validate-id-article {:id-article article-id})))))

(deftest test-validate-article-belongs-to-user
  (let [user (get-user (create-test-user!))]
    (is (nil? (validate-article-belongs-to-user
               user
               (get-review (create-test-review! :user user)))))
    (let [id (create-test-review!)]
      (is (= (INVALID-ID-ARTICLE id)
             (validate-article-belongs-to-user
              user
              (get-review id)))))))

(deftest test-update-review!
  (let [user (get-user (create-test-user!))
        article-id (create-test-article! :user user)
        review-id (create-review! (get-review-data {:id-article article-id}))
        new-captured-ref-id (create-test-captured-reference!)
        new-article-id (create-test-article! :user user)
        new-data {:id-article new-article-id
                  :feedback-text "NNNewwww feedback"
                  :status :completed}
        validate-id-article-args (atom nil)
        validate-review-status-args (atom nil)]
    (with-redefs [validate-id-article #(do (reset! validate-id-article-args %&) nil)
                  validate-review-status
                  #(do (reset! validate-review-status-args %&) nil)]
      (is (nil? (update-review! review-id new-data)))

      (testing "Uses validate-review-status"
        (is (= (list new-data) @validate-review-status-args)))

      (testing "Uses validate-id-article"
        (is (= (list new-data) @validate-id-article-args)))

      (testing "Updates values in the db"
        (is (= (get-review review-id)
               (assoc new-data :id review-id))))))

  (testing "uses validate-unique-review-for-article"
    (let [review-id           (create-test-review!)
          review-data         (get-review review-id)
          id-new-article      (create-test-article!)
          review-data-new-art (assoc review-data :id-article id-new-article)]
      ;; Same id-article, validate-unique-review is never called
      (with-redefs [validate-unique-review-for-article
                    (fn [x]
                      (is (= x review-data))
                      ::val-result)]
        (is (= nil (update-review! review-id review-data))))
      ;; New id-article, validate-unique-review fails
      (with-redefs [validate-unique-review-for-article
                    (fn [x]
                      (is (= x review-data-new-art))
                      ::val-result)]
        (is (= (->KtiError ::val-result)
               (update-review! review-id review-data-new-art)))))))

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

(deftest test-get-review-for-article
  (let [article (get-article (create-test-article!))]
    (is (nil? (get-review-for-article article)))
    (let [review (get-review
                  (create-review! (get-review-data {:id-article (article :id)})))]
      (is (= review (get-review-for-article article))))))

(deftest test-validate-unique-review-for-article
  (let [data {:id-article ::id}]

    (testing "Ok when review is unique"
      (with-redefs [get-review-for-article (fn [{id :id}]
                                             (is (= id ::id))
                                             nil)]
        (is (nil? (validate-unique-review-for-article data)))))

    (testing "When review is not unique"
      (with-redefs [get-review-for-article (fn [{id :id}]
                                             (is (= id ::id))
                                             ::val-result)]
        (is (= (DUPLICATED-REVIEW-FOR-ARTICLE ::id)
               (validate-unique-review-for-article data)))))))
