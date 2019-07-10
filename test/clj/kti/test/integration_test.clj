(ns kti.test.integration-test
  (:require [clojure.test :refer :all]
            [kti.test.helpers :refer :all]
            [kti.routes.services.tokens :refer [get-token-value]]
            [kti.routes.services.users :refer [get-user]]
            [kti.db.core :as db]
            [kti.db.articles :as db.articles]
            [kti.db.captured-references :as db.cap-refs]
            [java-time]
            [kti.utils :as utils]
            [ring.mock.request :refer :all]
            [mount.core :as mount]
            [kti.handler :refer [app]]
            [cheshire.core :as cheshire]))

;; Starts the app before the first test
(use-fixtures :once fixture-start-app-and-env)
(use-fixtures :each fixture-bind-db-to-rollback-transaction)


(deftest integration-tests-capturing-a-link
  (db.cap-refs/delete-all-captured-references)
  (db.articles/delete-all-articles)
  (let [link "https://www.youtube.com/watch?v=VON0rut5Pl8"
        created-id (atom nil)
        user (get-user (create-test-user!))
        token (get-token-value (create-test-token! user))]
    (testing "User captures a reference link from youtube"
      (let [response (-> (request :post "/api/captured-references")
                         (json-body {:reference link})
                         (auth-header token)
                         app)
            body (-> response :body body->map)]
        (is (= 201 (:status response)) (slurp (:body response)))
        (is (integer? (:id body)))
        (is (= (:reference body) link))
        (is (false? (:classified body)))
        (is (< (utils/seconds-between (-> body :created-at utils/str->date)
                                      (utils/now))
               3))
        ;; Stores the created id so others can use it
        (reset! created-id (:id body))))

    (testing "Gets all references and sees the one he captured there"
      (let [response (-> (request :get "/api/captured-references")
                         (auth-header token)
                         app)
            body (-> response :body body->map)]
        (is (ok? response))
        (is (= (count body) 1))
        (doseq [[key value] [[:id         @created-id]
                             [:reference  link]
                             [:classified false]]]
          (is (-> body first key (= value))))))

    (let [captured-reference-url (str "/api/captured-references/" @created-id)]
      (testing "Queries for the one he captured and sees it"
        (let [response (-> (request :get captured-reference-url)
                           (auth-header token)
                           app)
              body (-> response :body body->map)]
          (is (ok? response))
          (is (= (:id body) @created-id))
          (is (= (:reference body) link))))

      (testing "He sees that the link is wrong and updates it"
        (let [new-link "https://www.youtube.com/watch?v=aG2uddkKWYE"
              response (app (-> (request :put captured-reference-url)
                                (json-body {:reference new-link})
                                (auth-header token)))
              {raw-body :body status :status} response
              {:keys [id reference]} (body->map raw-body)]
          (is (ok? response))
          (is (= id @created-id))
          (is (= new-link reference)))))))
