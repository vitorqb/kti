(ns kti.test.routes.services.articles-test
  (:require [clojure.test :refer :all]
            [kti.utils :as utils]
            [kti.routes.services.articles :refer :all]
            [kti.routes.services.articles.base :refer :all]
            [kti.db.core :as db :refer [*db*]]
            [kti.routes.services.captured-references
             :refer [create-captured-reference!]]
            [kti.routes.services.captured-references.base
             :refer [get-captured-reference]]
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

(deftest test-get-article-for-captured-reference
  (let [{:keys [id] :as captured-reference}
        (get-captured-reference (create-test-captured-reference!))]
    (is (nil? (get-article-for-captured-reference captured-reference)))
    (let [article-id
          (create-article! (get-article-data {:id-captured-reference id}))]
      (is (= (get-article article-id)
             (get-article-for-captured-reference captured-reference))))))

(deftest test-update-article
  (let [captured-ref-id (create-test-captured-reference!)
        article-id (-> {:id-captured-reference captured-ref-id}
                       get-article-data
                       create-article!)
        new-captured-ref-id (create-test-captured-reference!)
        new-data {:id-captured-reference new-captured-ref-id
                  :description "blabla"
                  :action-link "www.goo.nl"
                  :tags #{"11" "22" "33"}}]

    (testing "validates with validate-article"
      (with-redefs [validate-article (fn [& _] (throw (Exception. "err")))]
        (is (thrown-with-msg? Exception #"err"
                              (update-article! article-id new-data)))))

    (testing "base"
      (update-article! article-id new-data)
      (is (= (assoc new-data :id article-id)
             (get-article article-id))))

    (testing "Calls create-missing-tags"
      (let [args (atom nil)]
        (with-redefs [create-missing-tags #(reset! args %&)]
          (update-article! article-id new-data))
        (is (= @args (list (:tags new-data))))))))

(deftest test-delete-article
  (let [id (create-article!
            (get-article-data
             {:id-captured-reference (create-test-captured-reference!)}))
        other ((comp get-article create-article! get-article-data)
               {:id-captured-reference (create-test-captured-reference!)})]
    (assert (not (nil? (get-article id))))
    (assert (not (= #{} (get-tags-for-article {:id id}))))
    (delete-article! id)
    (is (nil? (get-article id)))
    (is (= #{} (get-tags-for-article {:id id})))
    (is (= other (get-article (:id other))))))

(deftest test-set-tags-to-article
    (let [id 9988
          tags #{"tag-one" "two" "threee"}
          do-get-tags #(get-tags-for-article {:id id})]
      (is (= #{} (do-get-tags)))
      (set-tags-to-article! id tags)
      (is (= tags (do-get-tags)))))

(deftest test-validate-article
  (let [captured-ref-id (create-test-captured-reference!)
        inexistant-captured-ref-id 9288
        article-data (get-article-data)]
    (assert (nil? (get-captured-reference inexistant-captured-ref-id)))
    (testing "Inexistant captured ref"
      (is (thrown-with-msg?
           clojure.lang.ExceptionInfo
           (re-pattern (ARTICLE_ERR_INVALID_CAPTURED_REFERENCE_ID
                        inexistant-captured-ref-id))
           (validate-article (assoc article-data
                                          :id-captured-reference
                                          inexistant-captured-ref-id)))))))

(deftest test-clear-article-tags!
  (let [article-id 222 tags #{"abc" "def" "egh"}]
    (doseq [t tags]
      (jdbc/insert! *db* :articles_tags {:id_article article-id :id_tag t}))
    (is (= (get-tags-for-article {:id article-id}) tags))
    (clear-article-tags! article-id)
    (is (= (get-tags-for-article {:id article-id}) #{}))))

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

    (testing "Calls create-missing-tags"
      (let [args (atom nil)]
        (with-redefs [create-missing-tags #(reset! args %&)]
          (create-article! data))
        (is (= @args (list (:tags data)))))))
  
  (testing "Fails if captured-reference does not exists"
    (let [data (get-article-data {:id-captured-reference 291928173})]
      (assert (-> data :id-captured-reference get-captured-reference nil?))
      (is (thrown? clojure.lang.ExceptionInfo (create-article! data))))))

(deftest test-tag-exists?
  (let [tag "some-weeeeird-tag" do-tag-exists? #(tag-exists? tag)]
    (is (false? (do-tag-exists?)))
    (jdbc/insert! *db* :tags {:tag tag})
    (is (true? (do-tag-exists?)))))

(deftest test-create-missing-tags
  (let [existant-tag "aaaa" new-tag "bbbb" tags [existant-tag new-tag]]
    (jdbc/insert! *db* :tags {:tag existant-tag})
    (assert (tag-exists? existant-tag))
    (assert (not (tag-exists? new-tag)))
    (create-missing-tags tags)
    (is (every? tag-exists? tags))))
