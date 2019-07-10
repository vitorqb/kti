(ns kti.test.db.captured-references-test
  (:require 
   [kti.db.constants :as db.constants]
   [kti.db.captured-references :as sut]
   [clojure.test :refer :all]))

(deftest test-expand-filter-opts
  (are [i o] (= o (sut/expand-filter-opts i))

    {:has-article? ::db.constants/nofilter :has-review? ::db.constants/nofilter}
    {}

    {:has-article? true :has-review? ::db.constants/nofilter}
    {:filter.article-is-not-nil? true}

    {:has-article? false :has-review? true}
    {:filter.article-is-nil? true :filter.review-is-not-nil? true}))

(deftest test-parse-get-query-params
  (are [i o] (= o (sut/parse-get-query-params i))
    {}
    {:select (sut/snip-select)
     :filter (sut/snip-filter {})}

    {:filter-opts {:has-article? true}}
    {:select (sut/snip-select)
     :filter (sut/snip-filter {:filter.article-is-not-nil? true})}

    {:filter-opts {:has-review? false}
     :paginate-opts {:page 1 :page-size 2}}
    {:select (sut/snip-select)
     :filter (sut/snip-filter {:filter.review-is-nil? true})
     :paginate-opts {:page 1 :page-size 2}}))
