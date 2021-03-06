(ns kti.test.routes.services.articles-test
  (:require [clojure.test :refer :all]
            [kti.validation :refer [->KtiError validate]]
            [kti.utils :as utils]
            [kti.routes.services.users :refer [get-user]]
            [kti.routes.services.articles :refer :all]
            [kti.routes.services.articles.base :refer :all]
            [kti.routes.services.reviews.base :refer [get-review-for-article]]
            [kti.db.core :as db]
            [kti.db.tags :as db.tags]
            [kti.db.articles :as db.articles]
            [kti.db.state :refer [*db*]]
            [kti.routes.services.captured-references
             :refer [create-captured-reference!]]
            [kti.routes.services.captured-references.base
             :refer [get-captured-reference]]
            [kti.test.helpers
             :refer [clean-articles-and-tags
                     fixture-start-app-and-env
                     fixture-bind-db-to-rollback-transaction
                     create-test-captured-reference!
                     get-article-data
                     create-test-article!
                     create-test-user!]]
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
             (assoc data :id created-article-id)))))

  (testing "with user"
    (let [user {:id 22} id (create-test-article! :user user)]
      (is (= (get-article id user) (get-article id)))
      (is (nil? (get-article id {:id 33}))))))

(deftest test-get-article-for-captured-reference
  (let [{:keys [id] :as captured-reference}
        (get-captured-reference (create-test-captured-reference!))]
    (is (nil? (get-article-for-captured-reference captured-reference)))
    (let [article-id (create-test-article! :id-captured-reference id)]
      (is (= (get-article article-id)
             (get-article-for-captured-reference captured-reference))))))

(deftest test-update-article
  (let [user (get-user (create-test-user!))
        article-id (create-test-article! :user user)
        new-captured-ref-id (create-test-captured-reference! {:user user})
        new-data {:id-captured-reference new-captured-ref-id
                  :description "blabla"
                  :action-link "www.goo.nl"
                  :tags #{"11" "22" "33"}}]

    (testing "validates with validate-article-captured-reference-exists"
      (with-redefs [validate-article-captured-reference-exists (constantly "err")]
        (is (= (->KtiError "err") (update-article! article-id new-data)))))

    (testing "base"
      (is (nil? (update-article! article-id new-data)))
      (is (= (assoc new-data :id article-id)
             (get-article article-id))))

    (testing "Calls create-missing-tags"
      (let [args (atom nil)]
        (with-redefs [create-missing-tags #(reset! args %&)]
          (update-article! article-id new-data))
        (is (= @args (list (:tags new-data))))))

    (testing "Validates unique captured ref"
      (with-redefs [validate-unique-captured-reference (fn [&_] "bar")]
        (is (= (update-article! article-id (get-article-data))
               (->KtiError "bar")))))

    (testing "Updating to the same values is okay"
      (is (= nil (update-article! article-id (get-article article-id)))))

    (testing "Updating to a captured ref from other user fails"
      (let [id-new-cap-ref (create-test-captured-reference!)]
        (is (= (->KtiError (ARTICLE_ERR_INVALID_CAPTURED_REFERENCE_ID
                            id-new-cap-ref))
               (update-article!
                article-id
                (assoc new-data :id-captured-reference id-new-cap-ref))))))))

(deftest test-delete-article
  (testing "Base"
    (let [id (create-test-article!)
          other (get-article (create-test-article!))]
      (assert (not (nil? (get-article id))))
      (assert (not (= #{} (get-tags-for-article {:id id}))))
      (is (nil? (delete-article! id)))
      (is (nil? (get-article id)))
      (is (= #{} (get-tags-for-article {:id id})))
      (is (= other (get-article (:id other))))))
  (testing "Validates with validate-article-has-no-review"
    (with-redefs [validate-article-has-no-review (fn [&_] "bar")]
      (is (= (delete-article! {:id 1})
             (->KtiError "bar"))))))

(deftest test-set-tags-to-article
    (let [id 9988
          tags #{"tag-one" "two" "threee"}
          do-get-tags #(get-tags-for-article {:id id})]
      (is (= #{} (do-get-tags)))
      (set-tags-to-article! id tags)
      (is (= tags (do-get-tags)))))

(deftest test-validate-article-captured-reference-exists
  (let [captured-ref-id (create-test-captured-reference!)
        inexistant-captured-ref-id 9288
        article-data (get-article-data)]
    (assert (nil? (get-captured-reference inexistant-captured-ref-id)))
    (testing "Inexistant captured ref"
      (is (= (ARTICLE_ERR_INVALID_CAPTURED_REFERENCE_ID
              inexistant-captured-ref-id)
             (validate-article-captured-reference-exists (assoc article-data
                                      :id-captured-reference
                                      inexistant-captured-ref-id)))))))

(deftest test-validate-article-has-no-review
  (with-redefs [get-review-for-article (constantly nil)]
    (is (= nil (validate-article-has-no-review {:id 1})))) 
  (with-redefs [get-review-for-article (constantly {:id 2})]
    (is (= ERR-MSG-ARTICLE-HAS-REVIEW (validate-article-has-no-review {:id 1})))))

(deftest test-clear-article-tags!
  (let [article-id 222 tags #{"abc" "def" "egh"}]
    (doseq [t tags]
      (jdbc/insert! *db* :articles_tags {:id_article article-id :id_tag t}))
    (is (= (get-tags-for-article {:id article-id}) tags))
    (clear-article-tags! article-id)
    (is (= (get-tags-for-article {:id article-id}) #{}))))

(deftest test-get-user-articles
  (let [user (get-user (create-test-user!))]
    (testing "Empty" (is (= [] (get-user-articles user))))
    (testing "Two"
      (let [articles-ids [(create-test-article! :user user)
                          (create-test-article! :user user)]]
        (is (= (map get-article articles-ids) (get-user-articles user)))))
    (testing "Don't see from others"
      (let [other-user (get-user (create-test-user!))
            id (create-test-article! :user other-user)]
        (is (not ((->> (get-user-articles user) (map :id) (into #{}))
                  id)))))))

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
  (db.tags/delete-all-tags)

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
      (let [db-data (db.articles/get-article {:id article-id})]
        (is (= (:id_captured_reference db-data) captured-ref-id))
        (is (= (:id db-data) article-id))
        (is (= (:description db-data) (:description data)))
        (is (= (:action_link db-data) (:action-link data)))))

    (testing "Calls create-missing-tags"
      (let [args (atom nil) tags #{"foo" "bar"}]
        (with-redefs [create-missing-tags #(reset! args %&)
                      validate (constantly nil)]
          (create-article! (get-article-data {:tags tags})))
        (is (= @args (list tags))))))
  
  (testing "Fails if captured-reference does not exists"
    (let [data (get-article-data {:id-captured-reference 293})]
      (assert (-> data :id-captured-reference get-captured-reference nil?))
      (is (= (->KtiError (ARTICLE_ERR_INVALID_CAPTURED_REFERENCE_ID 293))
             (create-article! data)))))

  (testing "Fails if duplicated article for same captured-ref"
    (let [id-captured-reference (create-test-captured-reference!)]
      (with-redefs [validate-unique-captured-reference (fn [&_] "foo")]
        (is (= (->KtiError "foo")
               (-> {:id-captured-reference id-captured-reference}
                   get-article-data
                   create-article!))))))

  (testing "Fails if captured ref belongs to other user"
    (with-redefs [validate-article-captured-reference-belongs-to-user
                  (fn [& _] "baz")]
      (is (= (->KtiError "baz")
             (create-article! (get-article-data
                               {:id-captured-reference
                                (create-test-captured-reference!)})))))))

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

(deftest test-validate-unique-captured-reference
  (let [{:keys [id-captured-reference] :as article}
        (get-article (create-test-article!))]
    (assert ((comp not nil?) (get-article-for-captured-reference article)))
    (is (= (validate-unique-captured-reference
            {:id-captured-reference id-captured-reference})
           (ERR-MSG-DUPLICATED-CAPTURED-REFERENCE id-captured-reference))))
  (let [unused-id 999]
    (assert (nil? (get-article-for-captured-reference {:id unused-id})))
    (is (nil? (validate-unique-captured-reference
               {:id-captured-reference unused-id})))))
