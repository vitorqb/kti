(ns kti.utils)

(defn int-to-bool
  [x]
  (case x
    0 false
    1 true
    (throw (new Exception "Unexpected value for a bool?"))))

(defn str->date [str-date] (java-time/local-date-time str-date))
(defn now [] (java-time/local-date-time))
(defn seconds-between [x y] (java-time/time-between :seconds x y))
