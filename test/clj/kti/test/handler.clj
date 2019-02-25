(ns kti.test.handler
  (:require [clojure.test :refer :all]
            [ring.mock.request :refer :all]
            [kti.handler :refer :all]
            [kti.middleware.formats :as formats]
            [muuntaja.core :as m]
            [mount.core :as mount]))

(def url-inexistant-captured-reference "/api/captured-references/not-an-id")
(def captured-reference-data {:reference "some ref"})

(defn parse-json [body]
  (m/decode formats/instance "application/json" body))

(defn not-found? [response] (= 404 (:status response)))

(use-fixtures
  :once
  (fn [f]
    (mount/start #'kti.config/env
                 #'kti.handler/app)
    (f)))

(deftest test-app
  (testing "plus"
    (let [response (app (request :get "/api/plus" {:x 2 :y 3}))]
      (is (= 200 (:status response)))))

  (testing "not-found route"
    (let [response (app (request :get "/invalid"))]
      (is (not-found? response))))

  (testing "get captured reference id not found"
    (let [response (app (request :get url-inexistant-captured-reference))]
      (is (not-found? response))))

  (testing "put captured reference id not found"
    (let [response (app (-> (request :put url-inexistant-captured-reference)
                            (json-body captured-reference-data)))]
      (is (not-found? response)))))

