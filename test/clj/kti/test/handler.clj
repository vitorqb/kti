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
             :refer [create-article! get-user-articles
                     ARTICLE_ERR_INVALID_CAPTURED_REFERENCE_ID
                     ERR-MSG-ARTICLE-HAS-REVIEW]]
            [kti.routes.services.articles.base :refer [get-article]]
            [kti.routes.services.reviews
             :refer [create-review! get-review INVALID-ID-ARTICLE]]
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
          {:keys [id] :as body} (-> response :body body->map)]
      (is (ok? response))
      (are [k v] (= (get body k ::nf) v)
        :id id
        :reference reference
        :created-at (utils/date->str created-at)
        :classified false
        :article-id nil
        :review-id nil
        :review-status nil)
      ;; Now the user creates an article
      (is (ok? (-> (request :post "/api/articles")
                   (json-body {:id-captured-reference id
                               :description "Abc"
                               :tags []
                               :action-link nil})
                   (auth-header token)
                   app)))
      ;; And sees it on the get
      (let [response (run-request id token)
            {:keys [article-id classified]} (-> response :body body->map)]
        (is (not (nil? article-id)))
        (is (true? classified))
        ;; It finally creates a review
        (is (ok? (-> (request :post "/api/reviews")
                     (json-body {:id-article article-id
                                 :status "in-progress"
                                 :feedback-text ""})
                     (auth-header token)
                     app)))
        ;; And sees it on the get
        (let [response (run-request id token)
              {:keys [review-id review-status]} (-> response :body body->map)]
          (is (not (nil? review-id)))
          (is (= "in-progress" review-status)))))))

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
  (let [user (get-user (create-test-user!))
        token (get-token-value (create-test-token! user))]
    (letfn [(run-request [token] (-> (request :get "/api/articles")
                                     (cond-> token (auth-header token))
                                     app))]
      (testing "missing auth" (is (missing-auth? (run-request nil))))
      (testing "empty" (is (empty-response? (run-request token))))
      (testing "two articles"
        ;; Creates references and articles
        (let [captured-refs-data
              (map #(hash-map :reference % :user user) ["one" "two"])
              captured-refs-ids
              (doall (map create-captured-reference! captured-refs-data))
              articles-data
              [{:id-captured-reference (first captured-refs-ids)
                :description "Read blabla"
                :action-link "www.blabla.com"
                :tags ["book" "other"]}
               {:id-captured-reference (second captured-refs-ids)
                :description "Read how linux works"
                :action-link nil
                :tags ["book"]}]
              articles-ids (doall (map create-article! articles-data))
              response (run-request token)
              body (-> response :body body->map)]
          (is (ok? response))
          (is (= (count body) (count articles-ids)))
          (is (= (first body)
                 (assoc (first articles-data) :id (first articles-ids))))
          (is (= (second body)
                 (assoc (second articles-data) :id (second articles-ids))))))
      (testing "don't see from other users"
        (let [id (create-test-article!)
              token (get-token-value (create-test-token!))]
          (is (not ((->> (run-request token) :body body->map (map :id) (into #{}))
                    id))))))))

(deftest test-get-article
  (let [user (get-user (create-test-user!))
        token (get-token-value (create-test-token! user))
        article (get-article (create-test-article! :user user))
        run-request #(-> (request :get (str "/api/articles/" %1))
                         (cond-> %2 (auth-header %2))
                         app)]
    (testing "Auth required"
      (is (missing-auth? (run-request (:id article) nil))))
    (testing "Can't see other user's article"
      (is (not-found? (run-request (:id article)
                                   (get-token-value (create-test-token!))))))
    (testing "Base"
      (let [response (run-request (:id article) token)
            body (-> response :body body->map)]
        (is (ok? response))
        (is (= (ring-schema/coerce! Article body) article))))))
    
        
(deftest test-post-article
  (let [user (get-user (create-test-user!))
        token (get-token-value (create-test-token! user))
        data (get-article-data
              {:id-captured-reference
               (create-test-captured-reference! {:user user})})
        run-request #(-> (request :post "/api/articles")
                         (cond-> %1 (json-body %1))
                         (cond-> %2 (auth-header %2))
                         app)]
    (testing "Base"
      (let [response (run-request data token)
            {id :id :as body} (-> response :body body->map)]
        (is (= (:status response) 201))
        (is (integer? id))
        (is (= (-> body (dissoc :id) (update :tags set)) data))
        (is (= (ring-schema/coerce! Article body)
               (get-article id user)))
        (is (= [(ring-schema/coerce! Article body)]
               (get-user-articles user)))))
    (testing "400"
      (let [unkown-captured-ref-id 999
            wrong-data (assoc data :id-captured-reference unkown-captured-ref-id)
            response (run-request wrong-data token)]
        (is (= (response :status) 400))
        (is (= (-> response :body body->map)
               {:error-msg (ARTICLE_ERR_INVALID_CAPTURED_REFERENCE_ID
                            unkown-captured-ref-id)}))))
    (testing "Authz required"
      (is (missing-auth? (run-request data nil))))
    (testing "400 on captured ref from other user id"
      (let [other-user-cap-ref-id (create-test-captured-reference!)
            data (get-article-data {:id-captured-reference other-user-cap-ref-id})]
        (is (= 400 (:status (run-request data token))))))))

(deftest test-delete-article
  (letfn [(run-request [id token] (-> (request :delete (str "/api/articles/" id))
                                      (cond-> token (auth-header token))
                                      app))]
    (let [user (get-user (create-test-user!))
          token (get-token-value (create-test-token! user))]
      (testing "Base"
        (let [id (create-test-article! :user user) response (run-request id token)]
          (is (ok? response))
          (is (empty-response? response))
          (is (nil? (get-article id)))))
      (testing "404"
        (let [id 92837]
          (assert (nil? (get-article id)))
          (is (not-found? (run-request id token)))))
      (testing "400 (has review)"
        (let [id-article (create-test-article! :user user)
              id-review (create-review! (get-review-data {:id-article id-article}))
              response (run-request id-review token)]
          (is (= 400 (response :status)))
          (is (= ERR-MSG-ARTICLE-HAS-REVIEW
                 (-> response :body body->map :error-msg)))))
      (testing "Missing auth" (is (missing-auth? (run-request 1 nil))))
      (testing "404 if belongs to other user"
        (let [id (create-test-article!)]
          (is (not-found? (run-request id token))))))))

(deftest test-put-article
  (letfn [(run-request [id data token]
            (-> (request :put (str "/api/articles/" id))
                (cond-> data (json-body data))
                (cond-> token (auth-header token))
                app))]
    (let [user (get-user (create-test-user!))
          token (get-token-value (create-test-token! user))
          captured-ref-id (create-test-captured-reference! {:user user})
          new-captured-ref-id (create-test-captured-reference! {:user user})
          {id :id :as article} (get-article (create-test-article!
                                             :id-captured-reference
                                             captured-ref-id))
          new-data {:id-captured-reference new-captured-ref-id
                    :description "new description"
                    :action-link nil
                    :tags #{"tag-a" "tag-b"}}]
      (testing "base"
        (let [response (run-request id new-data token)
              body (-> response :body body->map)]
          (is (ok? response))
          (is (= (ring-schema/coerce! Article body) (assoc new-data :id id)))))
      (testing "error"
        (let [invalid-captured-reference-id 98765
              response (run-request
                        id
                        (assoc new-data
                               :id-captured-reference
                               invalid-captured-reference-id)
                        token)]
          (assert (nil? (get-captured-reference invalid-captured-reference-id)))
          (is (= (response :status) 400))
          (is (= (-> response :body body->map :error-msg)
                 (ARTICLE_ERR_INVALID_CAPTURED_REFERENCE_ID 98765)))))
      (testing "not found"
        (let [id 829]
          (is (nil? (get-article id)))
          (is (not-found? (run-request id new-data token)))))
      (testing "404 if other user"
        (is (not-found?
             (run-request id new-data (get-token-value (create-test-token!))))))
      (testing "Auth missing"
        (is (missing-auth? (run-request id new-data nil)))))))

(deftest test-post-reviews
  (letfn [(run-request [data token] (-> (request :post "/api/reviews")
                                        (json-body data)
                                        (cond-> token (auth-header token))
                                        app))]
    (let [user (get-user (create-test-user!))
          token (get-token-value (create-test-token! user))
          article-id (create-test-article! :user user)]
      (testing "Post data (base)"
        (let [data (get-review-data {:id-article article-id})
              response (run-request data token)
              body (-> response :body body->map)]
          (is (= (:status response) 201))
          (is (integer? (:id body)))
          (is (= (-> body (dissoc :id) (update :status keyword))
                 data))))
      (testing "Post with article from other user returns 400"
        (let [id-article (create-test-article!)
              data (get-review-data {:id-article id-article})
              response (run-request data token)]
          (is 400 (:status response))
          (is (= {:error-msg (INVALID-ID-ARTICLE id-article)}
                 (-> response :body body->map)))))
      (testing "Post with unkown article returns 400"
        (let [id-article 12321
              data (get-review-data {:id-article id-article})
              response (run-request data token)]
          (is 400 (:status response))
          (is (= {:error-msg (INVALID-ID-ARTICLE id-article)}
                 (-> response :body body->map)))))
      (testing "Missing auth"
        (is (missing-auth? (run-request (get-review-data) nil)))))))

(deftest test-put-reviews
  (letfn [(run-request [id data token] (-> (request :put (str "/api/reviews/" id))
                                           (json-body data)
                                           (cond-> token (auth-header token))
                                           app))]
    (let [user (get-user (create-test-user!))
          token (get-token-value (create-test-token! user))
          new-captured-reference-id (create-test-captured-reference!)
          new-article-id (create-test-article! :user user)
          new-data {:id-article new-article-id
                    :feedback-text "new feedback text!!!"
                    :status :discarded}]
      (testing "Not found"
        (let [inexistant-id 12928]
          (assert (nil? (get-review inexistant-id)))
          (is (not-found? (run-request inexistant-id new-data token)))))
      (testing "Found"
        (let [review-id (create-test-review! :user user)
              response (run-request review-id new-data token)
              body (-> response :body body->map)]
          (is (ok? response))
          (is (= (ring-schema/coerce! Review body)
                 (get-review review-id)
                 (assoc new-data :id review-id)))))
      (testing "Auth req.ed"
        (is (missing-auth? (run-request 1 new-data nil))))
      (testing "Can't put for other user"
        (is (not-found? (run-request (create-test-review!) new-data token))))
      (testing "Can't set article of other user"
        (let [id-article (create-test-article!)
              new-data (assoc new-data :id-article id-article)
              response (run-request (create-test-review! :user user) new-data token)]
          (is (= 400 (:status response)))
          (is (= {:error-msg (INVALID-ID-ARTICLE id-article)}
                 (-> response :body body->map))))))))

(deftest test-delete-review
  (let [run-request #(-> (request :delete (str "/api/reviews/" %1))
                         (cond-> %2 (auth-header %2))
                         app)
        user (get-user (create-test-user!))
        token (get-token-value (create-test-token! user))]
    (testing "Base"
      (let [id (create-test-review! :user user) response (run-request id token)]
        (is (ok? response))
        (is (nil? (get-review id)))))
    (testing "404"
      (let [id 2938]
        (is (not-found? (run-request id token)))))
    (testing "Auth req"
      (is (missing-auth? (run-request 1 nil))))
    (testing "404 if form other user"
      (is (not-found? (run-request (create-test-review!) token))))))

(deftest test-get-single-review
  (letfn [(run-request [id token] (-> (request :get (str "/api/reviews/" id))
                                      (cond-> token (auth-header token))
                                      app))]
    (let [user (get-user (create-test-user!))
          token (get-token-value (create-test-token! user))
          review (get-review (create-test-review! :user user))
          response (run-request (:id review) token)
          body (-> response :body body->map)]
      (is (ok? response))
      (is (= (ring-schema/coerce! Review body) review))
      (is (not-found? (run-request 99999 token)))
      (is (not-found? (run-request (create-test-review!) token))))))

(deftest test-get-many-reviews
  (let [user (get-user (create-test-user!))
        token (get-token-value (create-test-token! user))
        review (get-review (create-test-review! :user user))
        run-request #(-> (request :get "/api/reviews")
                         (cond-> % (auth-header %))
                         app)
        response (run-request token)
        body (-> response :body body->map)]
    (is (ok? response))
    (is (= (map #(ring-schema/coerce! Review %) body) [review]))
    (is (= [] (-> (run-request (get-token-value (create-test-token!)))
                  :body
                  body->map)))))


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
