(ns kti.test.utils-test
  (:require [clojure.test :refer :all]
            [kti.utils :refer :all]))

(deftest test-set-default
  (is (= (set-default {} :a 1) {:a 1}))
  (is (= (set-default {:b 2} :a 1) {:a 1 :b 2}))
  (is (= (set-default {} :a nil) {:a nil}))
  (is (= (set-default {:a 1} :a 2) {:a 1}))
  (is (= (set-default {:a nil} :a 1) {:a nil}))
  (is (= (set-default {:a nil :b nil} :b 2) {:a nil :b nil})))

