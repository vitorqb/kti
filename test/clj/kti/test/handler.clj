(ns kti.test.handler
  (:require [clojure.test :refer :all]
            [ring.mock.request :refer :all]
            [kti.handler :refer :all]
            [kti.routes.services :refer [Review Article]]
            [kti.utils :refer :all]
            [kti.test.helpers :refer :all]
            [clojure.java.jdbc :as jdbc]
            [kti.db.core :as db :refer [*db*]]
            [kti.routes.services.captured-references
             :refer [create-captured-reference!
                     get-captured-reference
                     DELETE-ERR-MSG-ARTICLE-EXISTS]]
            [kti.routes.services.articles
             :refer [create-article! get-article]]
            [kti.routes.services.reviews
             :refer [create-review! get-review]]
            [kti.utils :as utils]
            [kti.middleware.formats :as formats]
            [ring.swagger.schema :as ring-schema]
            [muuntaja.core :as m]
            [mount.core :as mount]))

(def url-inexistant-captured-reference "/api/captured-references/not-an-id")
(def captured-reference-data {:reference "some ref"})
(def JSON_FORMAT "" "application/json; charset=utf-8")
(def CONTENT_TYPE "" "Content-Type")

(use-fixtures :once fixture-start-app-and-env)
(use-fixtures :each fixture-bind-db-to-rollback-transaction)

(deftest test-returns-json-format
  ;; Any url should behave the same
  (let [response (app (request :get "/api/captured-references"))]
    (is (= JSON_FORMAT (get-in response [:headers CONTENT_TYPE])))))

(deftest test-get-captured-reference
  (db/delete-all-articles)
  (testing "get captured reference for a single id"
    (testing "not found"
      (let [response (app (request :get url-inexistant-captured-reference))]
        (is (not-found? response))))

    (testing "found"
      (let [data (get-captured-reference-data)
            captured-reference-id (create-captured-reference! data)
            response (->> captured-reference-id
                          (str "/api/captured-references/")
                          (request :get)
                          (app))
            body (-> response :body body->map)]
        (is (ok? response))
        (are [k v] (= (k body) v)
          :id captured-reference-id
          :reference (:reference data)
          :created-at (utils/date->str (:created-at data))
          :classified false))))

  (testing "get all captured references"
    (db/delete-all-captured-references)
    (let [run-get #(app (request :get "/api/captured-references"))]
      (testing "empty"
        (is (empty-response? (run-get))))

      (testing "two long"
        (let [datas [(get-captured-reference-data)
                     {:reference "two"
                      :created-at (java-time/local-date-time 1991 8 13)}]
              ids (doall (map create-captured-reference! datas))
              response (run-get)
              body (-> response :body body->map)]
          (is (ok? response))
          (is (= (count body) 2))
          (is (= (set ids) (->> body (map :id) set))))))))

(deftest test-put-captured-reference
  (let [make-url #(str "/api/captured-references/" %)
        make-request #(request :put (make-url %))]
    (testing "id not found"
      (let [response (app (-> (make-request "not-an-id")
                              (json-body captured-reference-data)))]
        (is (not-found? response))))

    (testing "Id found"
      (let [data (get-captured-reference-data)
            id (create-captured-reference! data)
            new-data {:reference "new-reeef"}
            response (app (-> (make-request id) (json-body new-data)))
            body (-> response :body body->map)]

        (testing "Response is okay"
          (is (ok? response))
          (is (= (:reference body) (:reference new-data))))

        (testing "Db was updated"
          (is (= (:reference (get-captured-reference id))
                 (:reference new-data))))))))

(deftest test-post-captured-reference
  (let [url "/api/captured-references"
        request (request :post url)
        response (-> request (json-body captured-reference-data) app)
        body (-> response :body body->map)]
    (testing "Returns 201"
      (is (= 201 (:status response))))
    (let [from-db (-> body :id get-captured-reference)]
      (testing "Creates on the db"
        (is (= (:reference from-db) (:reference captured-reference-data))))
      (testing "Returns created"
        (is (= (-> from-db :created-at date->str) (:created-at body)))
        (is (= (:reference body) (:reference from-db)))))))

(deftest test-delete-captured-reference
  (db/delete-all-articles)
  (testing "base"
    (let [id (create-test-captured-reference!)
          url (str "/api/captured-references/" id)
          response (app (request :delete url))]
      (is (ok? response))
      (is (empty-response? response))
      (is (nil? (get-captured-reference id)))))
  (testing "Error if article exists"
    (let [id (create-test-captured-reference!)]
      (create-article! (get-article-data {:id-captured-reference id}))
      (let [response (app (request :delete (str "/api/captured-references/" id)))]
        (is (= 400 (:status response)))
        (let [body (-> response :body body->map)]
          (is (= {:error-msg DELETE-ERR-MSG-ARTICLE-EXISTS}
                 body))))))
  (testing "404"
    (let [id 92839 url (str "/api/captured-references/" id)]
      (assert (nil? (get-captured-reference id)))
      (is (not-found? (app (request :delete url)))))))


(deftest test-get-articles
  (clean-articles-and-tags)
  (let [run-request #(app (request :get "/api/articles"))]
    (testing "empty"
      (is (empty-response? (run-request))))

    (testing "two articles"
      ;; Creates references and articles
      (let [captured-refs-data [{:reference "ref-one"} {:reference "ref-two"}]
            captured-refs-ids (doall (map create-captured-reference!
                                          captured-refs-data))
            articles-data [(get-article-data
                            {:id-captured-reference (first captured-refs-ids)
                             :tags []})
                           {:id-captured-reference (second captured-refs-ids)
                            :description "Read how linux works"
                            :action-link nil
                            :tags ["book"]}]
            articles-ids (doall (map create-article! articles-data))
            response (run-request)
            body (-> response :body body->map)]
        (is (ok? response))
        (is (= (count body) (count articles-ids)))
        (is (= (first body)
               (assoc (first articles-data) :id (first articles-ids))))
        (is (= (second body)
               (assoc (second articles-data) :id (second articles-ids))))))))

(deftest test-get-article
  (let [captured-ref-id (create-test-captured-reference!)
        article (-> {:id-captured-reference captured-ref-id}
                    get-article-data
                    create-article!
                    get-article)
        response (app (request :get (str "/api/articles/" (:id article))))
        body (-> response :body body->map)]
    (is (ok? response))
    (is (= (ring-schema/coerce! Article body) article))))
    
        
(deftest test-post-article
  (clean-articles-and-tags)
  (let [captured-ref-id (create-test-captured-reference!)
        data (get-article-data {:id-captured-reference captured-ref-id})
        response (-> (request :post "/api/articles")
                     (json-body data)
                     (app))
        body (-> response :body body->map)]
    (is (= (:status response) 201))
    (is (integer? (:id body)))
    (is (= (-> body (dissoc :id) (update :tags set)) data))))

(deftest test-delete-article
  (testing "Base"
    (let [captured-ref-id (create-test-captured-reference!)
          id (create-article!
              (get-article-data {:id-captured-reference captured-ref-id}))
          response (app (request :delete (str "/api/articles/" id)))]
      (is (ok? response))
      (is (empty-response? response)))
    (testing "404"
      (let [id 92837]
        (assert (nil? (get-article id)))
        (is (not-found? (app (request :delete (str "/api/articles/" id)))))))))

(deftest test-put-article
  (testing "put"
    (let [captured-ref-id (create-test-captured-reference!)
          new-captured-ref-id (create-test-captured-reference!)
          article (-> {:id-captured-reference captured-ref-id}
                      get-article-data
                      create-article!
                      get-article)
          new-data {:id-captured-reference new-captured-ref-id
                    :description "new description"
                    :action-link nil
                    :tags #{"tag-a" "tag-b"}}
          response (as-> article it
                     (:id it)
                     (str "/api/articles/" it)
                     (request :put it)
                     (json-body it new-data)
                     (app it))
          body (-> response :body body->map)]
      (is (= (ring-schema/coerce! Article body)
             (assoc new-data :id (:id article))))
      (testing "not found"
        (let [id 829]
          (is (nil? (get-article id)))
          (is (not-found? (as-> id it
                            (str "/api/articles/" it)
                            (request :put it)
                            (json-body it new-data)
                            (app it)))))))))

(deftest test-post-reviews
  (let [captured-ref-id (create-test-captured-reference!)
        article-id (create-article! (get-article-data
                                     {:id-captured-reference captured-ref-id}))]
    (testing "Post data (base)"
      (let [data (get-review-data {:id-article article-id})
            response (-> (request :post "/api/reviews")
                         (json-body data)
                         (app))
            body (-> response :body body->map)]
        (is (= (:status response) 201))
        (is (integer? (:id body)))
        (is (= (-> body (dissoc :id) (update :status keyword))
               data))))))

(deftest test-put-reviews
  (let [new-captured-reference-id (create-test-captured-reference!)
        new-article-id (create-article!
                        (get-article-data
                         {:id-captured-reference new-captured-reference-id}))
        new-data {:id-article new-article-id
                  :feedback-text "new feedback text!!!"
                  :status :discarded}]
    (testing "Not found"
      (let [inexistant-id 12928]
        (assert (nil? (get-review inexistant-id)))
        (is (not-found? (as-> inexistant-id it
                          (str "/api/reviews/" it)
                          (request :put it)
                          (json-body it new-data)
                          (app it)))))
    (testing "Found")
      (let [captured-reference-id (create-test-captured-reference!)
            article-id (create-article! (get-article-data
                                         {:id-captured-reference captured-reference-id}))
            review-id (create-review! (get-review-data {:id-article article-id}))
            response (as-> review-id it
                       (str "/api/reviews/" it)
                       (request :put it)
                       (json-body it new-data)
                       (app it))
            body (-> response :body body->map)]
        (is (ok? response))
        (is (= (ring-schema/coerce! Review body)
               (get-review review-id)
               (assoc new-data :id review-id)))))))

(deftest test-delete-review
  (testing "Base"
    (let [id (->> (create-test-captured-reference!)
                  (hash-map :id-captured-reference)
                  get-article-data
                  create-article!
                  (hash-map :id-article)
                  get-review-data
                  create-review!)          
          response (app (request :delete (str "/api/reviews/" id)))]
      (is (ok? response))
      (is (nil? (get-review id)))))
  (testing "404"
    (let [id 2938]
      (is (not-found? (app (request :delete (str "/api/reviews/" id))))))))

(deftest test-get-reviews
  (db/delete-all-reviews)
  (let [captured-ref-id (create-test-captured-reference!)
        article-id (create-article! (get-article-data
                                     {:id-captured-reference captured-ref-id}))
        review (-> {:id-article article-id}
                   get-review-data
                   create-review!
                   get-review)]
    (testing "Getting a single review"
      (let [response (app (request :get (str "/api/reviews/" (:id review))))
            body (-> response :body body->map)]
        (is (ok? response))
        (is (= (ring-schema/coerce! Review body) review))))

    (testing "Getting all reviews"
      (let [response (app (request :get "/api/reviews"))
            body (-> response :body body->map)]
        (is (ok? response))
        (is (= (map #(ring-schema/coerce! Review %) body) [review]))))))
