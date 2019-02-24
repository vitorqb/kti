(ns kti.test.handler
  (:require [clojure.test :refer :all]
            [ring.mock.request :refer :all]
            [kti.handler :refer :all]
            [kti.middleware.formats :as formats]
            [muuntaja.core :as m]
            [mount.core :as mount]))

(defn parse-json [body]
  (m/decode formats/instance "application/json" body))

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
      (is (= 404 (:status response)))))

  (testing "captured reference id not found"
    (let [response (app (request :get "/api/captured-references/not-an-id"))]
      (is (= 404 (:status response))))))

