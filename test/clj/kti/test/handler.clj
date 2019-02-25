(ns kti.test.handler
  (:require [clojure.test :refer :all]
            [clojure.java.jdbc :as jdbc]
            [kti.db.core :as db :refer [*db*]]
            [kti.routes.services.captured-references
             :refer [create-captured-reference!]]
            [kti.utils :as utils]
            [ring.mock.request :refer :all]
            [kti.handler :refer :all]
            [kti.middleware.formats :as formats]
            [muuntaja.core :as m]
            [mount.core :as mount]
            [cheshire.core :as cheshire]))

(def url-inexistant-captured-reference "/api/captured-references/not-an-id")
(def captured-reference-data {:reference "some ref"})

;; !!!! TODO -> Also use on integration
(defn not-found? [response] (= 404 (:status response)))
(defn ok? [response] (= 200 (:status response)))

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

(deftest test-get-captured-reference
  (testing "get captured reference id not found"
    (let [response (app (request :get url-inexistant-captured-reference))]
      (is (not-found? response))))

  (testing "get captured reference id"
    (jdbc/with-db-transaction [t-conn *db*]
      (jdbc/db-set-rollback-only! t-conn)
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
          (is (= (k body) v)))))))

(deftest test-put-captured-reference
  (testing "put captured reference id not found"
    (let [response (app (-> (request :put url-inexistant-captured-reference)
                            (json-body captured-reference-data)))]
      (is (not-found? response)))))
