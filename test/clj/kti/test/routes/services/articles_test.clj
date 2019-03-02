(ns kti.test.routes.services.articles-test
  (:require [clojure.test :refer :all]
            [kti.utils :as utils]
            [kti.routes.services.articles :refer :all]
            [kti.db.core :as db :refer [*db*]]
            [kti.routes.services.captured-references
             :refer [create-captured-reference!]]
            [kti.test.helpers
             :refer [clean-articles-and-tags
                     fixture-start-app-and-env
                     fixture-bind-db-to-rollback-transaction
                     create-test-captured-reference!
                     get-article-data]]
            [clojure.java.jdbc :as jdbc]
            [clojure.string :as str]
            [mount.core :as mount]))

(use-fixtures :once fixture-start-app-and-env)
(use-fixtures :each fixture-bind-db-to-rollback-transaction)

(deftest test-create-and-get-article
  (testing "base"
    (let [captured-ref-id (create-test-captured-reference!)
          data (get-article-data {:id-captured-reference captured-ref-id})
          created-article-id (create-article! data)]
      (is (= (get-article created-article-id)
             (assoc data :id created-article-id)))))

  (testing "many tags"
    (let [captured-ref-id (create-test-captured-reference!)
          data (get-article-data {:id-captured-reference captured-ref-id
                                  :tags #{"tag1" "tag2" "another"}})
          created-article-id (create-article! data)]
      (is (= (get-article created-article-id)
             (assoc data :id created-article-id))))))

(deftest test-get-all-articles
  (clean-articles-and-tags)
  (let [captured-reference-id (create-test-captured-reference!)
        data [(get-article-data {:id-captured-reference captured-reference-id})
              {:id-captured-reference captured-reference-id
               :description "Search for git book."
               :action-link "https://www.google.com/search?q=git+book"
               :tags #{}}]
        ids (doall (map create-article! data))
        gotten-articles (get-all-articles)]
    (is (= 2 (count gotten-articles)))
    (is (= (map :description gotten-articles) (map :description data)))
    (is (= (map (comp set :tags) gotten-articles) (map :tags data)))))

(deftest test-parse-article-data
  (let [raw-data {:id 1
                  :id_captured_reference 2
                  :description "hola"
                  :action_link nil
                  :tags "tag1 tag2"}]
    (testing "Base"
      (is (= (parse-article-data raw-data)
             {:id 1
              :id-captured-reference 2
              :description "hola"
              :action-link nil
              :tags #{"tag1" "tag2"}})))
    (testing "Handles nil tags"
      (is (= (-> raw-data (assoc :tags nil) parse-article-data :tags)
             #{})))))

(deftest test-create-tag!
  (db/delete-all-tags)

  (testing "base"
    (create-tag! "tag1")
    (is (= (count-tags) 1))
    (is (= (get-all-tags) #{"tag1"})))

  (testing "Uses validate-tag to validate before creating"
    (with-redefs [validate-tag (constantly "err-msg")]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"err-msg"
                            (create-tag! "mytag"))))
    (with-redefs [validate-tag (constantly nil)]
      (create-tag! "invalid tag suceeds"))))

(deftest test-validate-tag
  (is (= (validate-tag "normal_tag")
         nil))
  (is (= (validate-tag "normal-tag")
         nil))
  (is (= (validate-tag "normal123tag")
         nil))
  (is (= (validate-tag "normal tag")
         TAG_ERR_INVALID_CHARS))
  (is (= (validate-tag "normal/tag")
         TAG_ERR_INVALID_CHARS))
  (is (= (validate-tag (->> "t" (repeat (inc MAX_TAG_LENGTH)) str/join))
         TAG_ERR_TOO_LONG))
  (is (= (validate-tag (->> "t" (repeat (dec MIN_TAG_LENGTH)) str/join))
         TAG_ERR_TOO_SHORT)))

(deftest test-create-article
  (clean-articles-and-tags)
  (let [captured-ref-id (create-test-captured-reference!)
        data (get-article-data {:id-captured-reference captured-ref-id
                                :tags #{"tag1" "tag2"}})
        article-id (create-article! data)]
    (testing "Creates tags"      
      (is (= (get-all-tags) (set (:tags data))))
      (is (= (get-tags-for-article {:id article-id}) (:tags data))))

    (testing "Adds values to db"
      (let [db-data (db/get-article {:id article-id})]
        (is (= (:id_captured_reference db-data) captured-ref-id))
        (is (= (:id db-data) article-id))
        (is (= (:description db-data) (:description data)))
        (is (= (:action_link db-data) (:action-link data)))))

    (testing "Reverts changes on failure"
      ;; We can not test the transaction because we are already wrapped
      ;; in another transaction (for test). So the best we can do is to
      ;; be sure that all db/create-* were called with (with-db-transaction)
      (let [used-args (atom {})]
        (with-redefs [db/create-article-tag!
                      #(swap! used-args assoc :create-article-tag! %&)
                      db/create-article!
                      #(swap! used-args assoc :create-article! %&)
                      db/create-tag!
                      #(swap! used-args assoc :create-tag! %&)]
          (create-article! (get-article-data))
          ;; Each create should have received 2 args (transaction + data)
          ;; and the transaction should not have been the same as global *db*
          (are [k] (and (= (count (k @used-args)) 2)
                        (not= (first (k @used-args)) *db*))
            :create-article!
            :create-article-tag!
            :create-tag!))))))
