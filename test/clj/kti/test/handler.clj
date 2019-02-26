(ns kti.test.handler
  (:require [clojure.test :refer :all]
            [ring.mock.request :refer :all]
            [kti.handler :refer :all]
            [kti.utils :refer :all]
            [kti.test.helpers :refer [not-found? ok?]]
            [clojure.java.jdbc :as jdbc]
            [kti.db.core :as db :refer [*db*]]
            [kti.routes.services.captured-references
             :refer [create-captured-reference!
                     get-captured-reference]]
            [kti.utils :as utils]
            [kti.middleware.formats :as formats]
            [muuntaja.core :as m]
            [mount.core :as mount]
            [cheshire.core :as cheshire]))

(def url-inexistant-captured-reference "/api/captured-references/not-an-id")
(def captured-reference-data {:reference "some ref"})
(def JSON_FORMAT "" "application/json; charset=utf-8")
(def CONTENT_TYPE "" "Content-Type")

;; !!!! TODO -> Repeated from integration_test
(defn body->map
  "Converts a response body into a map. Assumes it is json."
  [json-body]
  (cheshire/parse-string (slurp json-body) true))

(use-fixtures
  :once
  (fn [f]
    (mount/start #'kti.config/env
                 #'kti.handler/app)
    (f)))

(use-fixtures
  :each
  (fn [f]
    (jdbc/with-db-transaction [t-conn *db*]
      (jdbc/db-set-rollback-only! t-conn)
      (binding [*db* t-conn]
        (f)))))

(deftest test-returns-json-format
  ;; Any url should behave the same
  (let [response (app (request :get "/api/captured-references"))]
    (is (= JSON_FORMAT (get-in response [:headers CONTENT_TYPE])))))

(deftest test-get-captured-reference
  (testing "get captured reference for a single id"
    (testing "not found"
      (let [response (app (request :get url-inexistant-captured-reference))]
        (is (not-found? response))))

    (testing "found"
      (let [created-at (java-time/local-date-time 1993 11 23)
            captured-reference-id (-> captured-reference-data
                                      (assoc :created-at created-at)
                                      create-captured-reference!)
            response (->> captured-reference-id
                          (str "/api/captured-references/")
                          (request :get)
                          (app))
            body (-> response :body body->map)]
        (is (ok? response))
        (doseq [[k v] [[:id         captured-reference-id]
                       [:reference  (:reference captured-reference-data)]
                       [:created-at (utils/date->str created-at)]
                       [:classified false]]]
          (is (= (k body) v))))))

  (testing "get all captured references"
    (db/delete-all-captured-references)
    (let [run-get #(app (request :get "/api/captured-references"))]
      (testing "empty"
        (let [response (run-get)
              body (-> response :body body->map)]
          (is (ok? response))
          (is (= body []))))

      (testing "two long"
        (let [first-created-id
              (create-captured-reference!
               {:reference "one"
                :created-at (java-time/local-date-time 1993 11 23)})
              second-created-id
              (create-captured-reference!
               {:reference "two"
                :created-at (java-time/local-date-time 1991 8 13)})
              response (run-get)
              body (-> response :body body->map)]
          (is (ok? response))
          (is (= (count body) 2))
          (is (= #{first-created-id second-created-id}
                 (->> body (map :id) (into #{})))))))))

(deftest test-put-captured-reference
  (let [make-url #(str "/api/captured-references/" %)
        make-request #(request :put (make-url %))]
    (testing "id not found"
      (let [response (app (-> (make-request "not-an-id")
                              (json-body captured-reference-data)))]
        (is (not-found? response))))

    (testing "Id found"
      (let [id (->> (java-time/local-date-time 2018 1 1)
                    (assoc captured-reference-data :created-at)
                    (create-captured-reference!))
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
