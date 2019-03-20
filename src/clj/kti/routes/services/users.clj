(ns kti.routes.services.users
  (:require [kti.db.core :as db]))

(defn get-user-from-token [token]
  (and token (db/get-user-from-token {:token-value token})))
