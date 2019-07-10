(ns kti.db.tokens
  (:require
   [kti.db.state :refer [*db*]]
   [conman.core :as conman]))

(conman/bind-connection *db* "sql/tokens.sql")

