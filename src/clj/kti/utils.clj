(ns kti.utils
  (require [java-time]))

(defn int-to-bool
  [x]
  (case x
    0 false
    1 true
    (throw (new Exception "Unexpected value for a bool?"))))

(def date-format "yyyy-MM-dd'T'HH:mm:ss")
(defn str->date [str-date] (java-time/local-date-time date-format str-date))
(defn date->str [date] (java-time/format date-format date))
(defn now [] (java-time/local-date-time))
(defn seconds-between [x y] (java-time/time-between :seconds x y))
