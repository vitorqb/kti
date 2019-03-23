(ns kti.test.handler
  (:require [clojure.test :refer :all]
            [ring.mock.request :refer :all]
            [kti.http :refer [send-email]]
            [kti.handler :refer :all]
            [kti.routes.services :refer [Review Article]]
            [kti.routes.services.tokens
             :refer [gen-token get-current-token-for-email give-token!
                     get-token-value]]
            [kti.routes.services.users.base
             :refer [get-user-by-email]]
            [kti.utils :refer :all]
            [kti.test.helpers :refer :all]
            [clojure.java.jdbc :as jdbc]
            [kti.db.core :as db :refer [*db*]]
            [kti.routes.services.captured-references
             :refer [create-captured-reference!
                     DELETE-ERR-MSG-ARTICLE-EXISTS
                     validate-captured-ref-reference-min-length
                     ERR-MSG-REFERENCE-MIN-LENGTH]]
            [kti.routes.services.captured-references.base
             :refer [get-captured-reference]]
            [kti.routes.services.articles
             :refer [create-article! get-article
                     ARTICLE_ERR_INVALID_CAPTURED_REFERENCE_ID
                     ERR-MSG-ARTICLE-HAS-REVIEW]]
            [kti.routes.services.reviews
             :refer [create-review! get-review]]
            [kti.routes.services.users :refer [get-user get-user-for]]
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

(deftest test-get-single-captured-reference
  (let [user (get-user (create-test-user!))
          token (get-token-value (create-test-token! user))
          {:keys [id reference created-at]}
          (get-captured-reference
           (create-test-captured-reference! {:user user}))
          run-request #(-> (request :get (str "/api/captured-references/" %1))
                           (cond-> %2 (auth-header %2))
                           app)]
      (is (missing-auth? (run-request 1 nil)))
      (is (not-found? (run-request 213829123 token)))
      (let [other-user-token (get-token-value (create-test-token!))]
        (is (not-found? (run-request id other-user-token))))
      (let [response (run-request id token)
            body (-> response :body body->map)]
        (is (ok? response))
        (are [k v] (= (k body) v)
          :id id
          :reference reference
          :created-at (utils/date->str created-at)
          :classified false))))

(deftest test-get-all-captured-reference
  (letfn [(run-get [token]
            (-> (request :get "/api/captured-references")
                (cond-> token (auth-header token))
                app))]
    (testing "empty"
      (let [token (get-token-value (create-test-token!))]
        (is (empty-response? (run-get token)))))
    (testing "missing auth" (is (missing-auth? (run-get nil))))
    (testing "two long same user"
      (let [user (get-user (create-test-user!))
            token (get-token-value (create-test-token! user))
            datas [(get-captured-reference-data {:user user})
                   {:reference "two"
                    :created-at (java-time/local-date-time 1991 8 13)
                    :user user}]
            ids (doall (map create-captured-reference! datas))
            response (run-get token)
            body (-> response :body body->map)]
        (is (ok? response))
        (is (= (count body) 2))
        (is (= (set ids) (->> body (map :id) set)))))
    (testing "Can't see form other user"
      (let [id (create-test-captured-reference!)
            token (get-token-value (create-test-token!))]
        (is (= 0 (-> token run-get :body body->map count)))))))

(deftest test-put-captured-reference
  (let [run-request #(-> (request :put (str "/api/captured-references/" %1))
                         (json-body %2)
                         (cond-> %3 (auth-header %3))
                         app)
        user (get-user (create-test-user!))
        token (get-token-value (create-test-token! user))]
    (is (not-found? (run-request "invalid-id" captured-reference-data token)))
    (let [id (create-captured-reference! (get-captured-reference-data {:user user}))
          new-data {:reference "new-reeef"}
          other-user-token (get-token-value (create-test-token!))]
      (is (not-found? (run-request id new-data other-user-token)))
      (let [response (run-request id new-data token)
            body (-> response :body body->map)]
        (is (ok? response))
        (is (= (:reference body)
               (:reference new-data)
               (:reference (get-captured-reference id))))))      

    (testing "Validation error for ref length"
        (let [id (create-test-captured-reference! {:user user})
              new-data {:reference "bar"}
              response (with-redefs [validate-captured-ref-reference-min-length
                                     (fn [&_] "foo")]
                         (run-request id new-data token))]
          (is (= 400 (response :status)))
          (is (= "foo" (-> response :body body->map :error-msg)))))))        

(deftest test-post-captured-reference
  (let [user (get-user (create-test-user!))
        token (get-token-value (create-test-token! user))
        url "/api/captured-references"
        run-request #(-> (request :post "/api/captured-references")
                         (cond-> %1 (json-body %1))
                         (cond-> %2 (auth-header %2))
                         app)
        request (request :post url)]
    (is (missing-auth? (run-request {:reference ""} nil)))        
    (let [response (run-request captured-reference-data token)
          body (-> response :body body->map)]
      (testing "Returns 201"
        (is (= 201 (:status response))))
      (let [from-db (-> body :id get-captured-reference)]
        (is (= (:reference from-db) (:reference captured-reference-data)))
        (is (= user (get-user-for :captured-reference from-db)))
        (is (= (:created-at body) (-> from-db :created-at date->str)))
        (is (= (:reference body) (:reference from-db)))))
    (testing "Validates minimum reference length"
      (with-redefs [validate-captured-ref-reference-min-length (fn [&_] "foobar")]
        (let [response (run-request captured-reference-data token)]
          (is (= 400 (response :status)))
          (is (= {:error-msg "foobar"} (-> response :body body->map))))))))

(deftest test-delete-captured-reference
  (letfn [(run-request [id token]
            (-> (request :delete (str "/api/captured-references/" id))
                (cond-> token (auth-header token))
                app))]
    (let [user (get-user (create-test-user!))
          token (get-token-value (create-test-token! user))]
      (testing "base"
        (let [id (create-test-captured-reference! {:user user})
              response (run-request id token)]
          (is (ok? response))
          (is (empty-response? response))
          (is (nil? (get-captured-reference id)))))
      (testing "404 if from another user"
        (let [other-user (get-user (create-test-user!))
              token (get-token-value (create-test-token! other-user))
              id (create-test-captured-reference! {:user user})]
          (is (not-found? (run-request id token)))))
      (testing "400 if missing header"
        (is (missing-auth? (run-request 1 nil))))
      (testing "404 on invalid id"
        (is (not-found? (run-request 222 token))))
      (testing "Error if article exists"
        (let [id (create-test-captured-reference! {:user user})]
          (create-test-article! :id-captured-reference id)
          (let [response (run-request id token)]
            (is (= 400 (:status response)))
            (is (= {:error-msg DELETE-ERR-MSG-ARTICLE-EXISTS}
                   (-> response :body body->map)))))))))

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
  (let [article (get-article (create-test-article!))
        response (app (request :get (str "/api/articles/" (:id article))))
        body (-> response :body body->map)]
    (is (ok? response))
    (is (= (ring-schema/coerce! Article body) article))))
    
        
(deftest test-post-article
  (clean-articles-and-tags)
  (let [captured-ref-id (create-test-captured-reference!)
        data (get-article-data {:id-captured-reference captured-ref-id})]
    (testing "Base"
      (let [response (-> (request :post "/api/articles")
                         (json-body data)
                         (app))
            body (-> response :body body->map)]
        (is (= (:status response) 201))
        (is (integer? (:id body)))
        (is (= (-> body (dissoc :id) (update :tags set)) data))))
    (testing "400"
      (let [unkown-captured-ref-id 999]
        (assert (nil? (get-captured-reference unkown-captured-ref-id)))
        (let [wrong-data
              (assoc data :id-captured-reference unkown-captured-ref-id)
              response (-> (request :post "/api/articles")
                           (json-body wrong-data)
                           (app))]
          (is (= (response :status) 400))
          (is (= (-> response :body body->map)
                 {:error-msg (ARTICLE_ERR_INVALID_CAPTURED_REFERENCE_ID
                              unkown-captured-ref-id)})))))))

(deftest test-delete-article
  (letfn [(run-request [id] (app (request :delete (str "/api/articles/" id))))]
    (testing "Base"
      (let [id (create-test-article!)
            response (run-request id)]
        (is (ok? response))
        (is (empty-response? response)))
      (testing "404"
        (let [id 92837]
          (assert (nil? (get-article id)))
          (is (not-found? (run-request id)))))
      (testing "400"
        (testing "Has review"
          (let [id-article (create-test-article!)
                id-review (create-review! (get-review-data {:id-article id-article}))
                response (run-request id-review)]
            (is (= 400 (response :status)))
            (is (= ERR-MSG-ARTICLE-HAS-REVIEW
                   (-> response :body body->map :error-msg)))))))))

(deftest test-put-article
  (testing "put"
    (let [captured-ref-id (create-test-captured-reference!)
          new-captured-ref-id (create-test-captured-reference!)
          {id :id :as article} (get-article (create-test-article!
                                             :id-captured-reference
                                             captured-ref-id))
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
      (testing "error"
        (let [invalid-captured-reference-id 98765
              response (app (-> (request :put (str "/api/articles/" id))
                                (json-body
                                 (assoc new-data
                                        :id-captured-reference
                                        invalid-captured-reference-id))))]
          (assert (nil? (get-captured-reference invalid-captured-reference-id)))
          (is (= (response :status) 400))
          (is (= (-> response :body body->map :error-msg)
                 (ARTICLE_ERR_INVALID_CAPTURED_REFERENCE_ID 98765)))))
      (testing "not found"
        (let [id 829]
          (is (nil? (get-article id)))
          (is (not-found? (as-> id it
                            (str "/api/articles/" it)
                            (request :put it)
                            (json-body it new-data)
                            (app it)))))))))

(deftest test-post-reviews
  (let [article-id (create-test-article!)]
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
        new-article-id (create-test-article!)
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
            article-id (create-test-article!)
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
  (let [run-request #(app (request :delete (str "/api/reviews/" %)))]
    (testing "Base"
      (let [id (->> (create-test-article!)
                    (hash-map :id-article)
                    get-review-data
                    create-review!)          
            response (run-request id)]
        (is (ok? response))
        (is (nil? (get-review id)))))
    (testing "404"
      (let [id 2938]
        (is (not-found? (app (request :delete (str "/api/reviews/" id)))))))))

(deftest test-get-reviews
  (db/delete-all-reviews)
  (let [article-id (create-test-article!)
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


(deftest test-post-token
  (let [make-request #(-> (request :post "/api/token")
                          (json-body {:email %})
                          app)]
    (testing "Base"
      (let [send-email-args (atom []) token "1234567890" email "vitorqb@gmail.com"]
        (with-redefs [send-email #(swap! send-email-args conj %&)
                      gen-token (constantly token)]
          (let [response (make-request email)]
            (is (= 204 (:status response)))
            (is (= @send-email-args [[email (str "Your token is: " token)]]))
            (is (= (get-current-token-for-email email) token))))))
    (testing "Creates new user if needed"
      (let [email "a1234@bbcs.f"]
        (is (nil? (get-user-by-email email)))
        (with-redefs [send-email (constantly nil)]
          (make-request email)
          (is (= (:email (get-user-by-email email)) email)))))
    (testing "Calls give-token!"
      (let [email "a@b.com" give-token!-args (atom [])]
        (with-redefs [give-token! #(swap! give-token!-args conj %&)]
          (make-request email)
          (is (= @give-token!-args [[email]])))))))
