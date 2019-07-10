(ns kti.db.tags
  (:require
   [kti.db.state :refer [*db*]]
   [conman.core :as conman]))

(conman/bind-connection *db* "sql/tags.sql")
