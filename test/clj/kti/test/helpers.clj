(ns kti.test.helpers
  (:require [ring.mock.request :refer :all]))

(defn not-found? [response] (= 404 (:status response)))
(defn ok? [response] (= 200 (:status response)))
   
