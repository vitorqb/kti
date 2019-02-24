(ns kti.test.integration-test
  (:require [clojure.test :refer :all]
            [kti.db.core :as db]
            [java-time]
            [kti.utils :as utils]
            [ring.mock.request :refer :all]
            [mount.core :as mount]
            [kti.handler :refer [app]]
            [cheshire.core :as cheshire]))


(def JSON_FORMAT "" "application/json; charset=utf-8")
(def CONTENT_TYPE "" "Content-Type")

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
        (is (= JSON_FORMAT (get-in response [:headers CONTENT_TYPE])))
        (is (integer? (:id body)))
        (is (= (:reference body) link))
        (is (false? (:classified body)))
        (is (< (java-time/time-between :seconds
                                       (-> body :created-at utils/str->date)
                                       (utils/now))
               3))
        ;; Stores the created id so others can use it
        (reset! created-id (:id body))))

    (testing "Gets all references and sees the one he captured there"
      (let [response (app (request :get "/api/captured-references"))
            body (-> response :body body->map)]
        (is (= 200 (:status response)) (str response))
        (is (= JSON_FORMAT (get-in response [:headers CONTENT_TYPE])))
        (is (= (count body) 1))
        (doseq [[key value] [[:id         @created-id]
                             [:reference  link]
                             [:classified false]]]
          (is (-> body first key (= value))))))

    (testing "Queries for the one he captured and sees it"
      (let [response (-> "/api/captured-references/"
                         (str @created-id)
                         (->> (request :get))
                         app)
            body (-> response :body body->map)]
        (is (= 200 (:status response)) body)
        (is (= (:id body) @created-id))
        (is (= (:reference body) link))))))
