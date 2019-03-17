(ns kti.routes.services.users.base
  (:require [kti.db.core :as db :refer [*db*]]))

(defn get-user-by-email [email]
  (db/get-user-by-email {:email email}))

(defn create-user! [params]
  (-> (db/create-user! params) (get (keyword "last_insert_rowid()"))))
