(ns kti.routes.services.users.base
  (:require [kti.db.core :as db]
            [kti.db.user :as db.user]
            [kti.db.state :refer [*db*]]))

(defn get-user-by-email [email]
  (db.user/get-user-by-email {:email email}))

(defn create-user! [params]
  (-> (db.user/create-user! params) (get (keyword "last_insert_rowid()"))))
