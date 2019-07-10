(ns kti.db.user
  (:require
   [kti.db.state :refer [*db*]]
   [conman.core :as conman]))

(conman/bind-connection *db* "sql/user.sql")
