(ns kti.routes.services.users
  (:require [kti.db.core :as db]
            [kti.db.user :as db.user]))

(defn get-user-from-token [token]
  (and token (db.user/get-user-from-token {:token-value token})))

(defn get-user [id] (db.user/get-user {:id id}))

(defmulti get-user-for (fn [type _] type))
(defmethod get-user-for :captured-reference [_ m]
  (db.user/get-user-for-captured-reference m))
(defmethod get-user-for :article [_ m]
  (get-user-for :captured-reference {:id (:id-captured-reference m)}))
(defmethod get-user-for :review [_ m] (db.user/get-user-for-review m))
