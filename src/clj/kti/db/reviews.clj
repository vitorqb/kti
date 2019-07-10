(ns kti.db.reviews
  (:require
   [kti.db.state :refer [*db*]]
   [conman.core :as conman]))

(conman/bind-connection *db* "sql/reviews.sql")

