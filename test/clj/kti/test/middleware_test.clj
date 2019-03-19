(ns kti.test.middleware-test
  (:require [clojure.test :refer :all]
            [kti.middleware :refer :all]))

(deftest test-bind-user []
  (testing ":user is nil if extract-user returns nil"
    (let [handler-args (atom [])
          handler #(swap! handler-args conj %&)]
      ((bind-user handler (constantly nil)) {})
      (is (= [[{}]] @handler-args))))
  (testing ":user is not nil if extract-user returns non-nil"
    (let [handler-args (atom [])
          handler #(swap! handler-args conj %&)]
      ((bind-user handler (constantly :foo)) {})
      (is (= [[{:user :foo}]] @handler-args))))
  (testing "Returns same as handler"
    (is (= ((bind-user (constantly :bar) (constantly nil)) {})
           :bar))))

(deftest test-extract-token []
  (is (= nil (extract-token {})))
  (is (= nil (extract-token {:headers {}})))
  (is (= nil (extract-token {:headers {"authorization" "INVALID FORMAT"}})))
  (is (= nil (extract-token {:headers {"authorization" "TOKEN I N V A L I D"}})))
  (is (= "123" (extract-token {:headers {"authorization" "TOKEN 123"}})))
  (is (= "123" (extract-token {:headers {"AUTHORIZATION" "token 123"}}))))
