(ns kti.test.integration-test
  (:require [clojure.test :refer :all]
            [kti.test.helpers :refer [not-found? ok?]]
            [kti.db.core :as db]
            [java-time]
            [kti.utils :as utils]
            [ring.mock.request :refer :all]
            [mount.core :as mount]
            [kti.handler :refer [app]]
            [cheshire.core :as cheshire]))

;; !!!! TODO -> Major tests refactoring
(defn body->map
  "Converts a response body into a map. Assumes it is json."
  [json-body]
  (cheshire/parse-string (slurp json-body) true))

;; Starts the app before the first test
(use-fixtures
  :once
  (fn [f]
    (mount/start #'kti.config/env
                 #'kti.handler/app)
    (f)))

(deftest integration-tests-capturing-a-link
  (db/delete-all-captured-references)
  (let [link "https://www.youtube.com/watch?v=VON0rut5Pl8"
        created-id (atom nil)]
    (testing "User captures a reference link from youtube"
      (let [response (app (-> (request :post "/api/captured-references")
                              (json-body {:reference link})))
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
      (let [response (app (request :get "/api/captured-references"))
            body (-> response :body body->map)]
        (is (ok? response))
        (is (= (count body) 1))
        (doseq [[key value] [[:id         @created-id]
                             [:reference  link]
                             [:classified false]]]
          (is (-> body first key (= value))))))

    (let [captured-reference-url (str "/api/captured-references/" @created-id)]
      (testing "Queries for the one he captured and sees it"
        (let [response (app (request :get captured-reference-url))
              body (-> response :body body->map)]
          (is (ok? response))
          (is (= (:id body) @created-id))
          (is (= (:reference body) link))))

      (testing "He sees that the link is wrong and updates it"
        (let [new-link "https://www.youtube.com/watch?v=aG2uddkKWYE"
              response (app (-> (request :put captured-reference-url)
                                (json-body {:reference new-link})))
              {raw-body :body status :status} response
              {:keys [id reference]} (body->map raw-body)]
          (is (ok? response))
          (is (= id @created-id))
          (is (= new-link reference)))))))
