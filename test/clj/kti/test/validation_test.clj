(ns kti.test.validation-test
  (:require [clojure.test :refer :all]
            [kti.validation :refer :all]))

(deftest test-validate
  (is (nil? (validate 1)))
  (is (= {:error-msg "error"} (validate 1 (constantly "error"))))
  (is (= nil (validate 1 (constantly nil))))
  (is (= nil (validate 1 (constantly nil) (constantly nil))))
  (is (= {:error-msg "foobar"} (validate 1
                                 (constantly nil)
                                 (constantly "foobar")
                                 (constantly nil)))))
