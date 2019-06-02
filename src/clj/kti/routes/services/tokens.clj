(ns kti.routes.services.tokens
  (:require [kti.db.core :as db :refer [*db*]]
            [kti.http :as kti-http]
            [kti.routes.services.users.base :refer [get-user-by-email
                                                    create-user!]]
            [clojure.java.jdbc :as jdbc]))

(def TOKEN-LENGTH 80)
(def TOKEN-CHARS
  (into #{} "1234567890QWERTYUIOPASDFGHJKLZXCVBNMqwertyuiopasdfghjklzxcvbnm"))

(defn get-all-token-values []
  (->> (db/get-all-token-values) (map :value)))

(defn get-token-value [id] (some-> {:id id} db/get-token-value :value))

(defn send-token-by-email! [email value]
  (kti-http/send-email email (str "Your token is: " value)))

(defn gen-token []
  (let [chars (into [] TOKEN-CHARS)
        existing-tokens (into #{} (get-all-token-values))
        generate-one
        #(reduce (fn [acc _] (str acc (rand-nth chars))) (range TOKEN-LENGTH))]
    (some #(when-not (existing-tokens %) %) (repeatedly generate-one))))

(defn get-current-token-for-email [email]
  (:value (db/get-current-token-for-email {:email email})))

(defn delete-tokens-for-user! [user] (db/delete-tokens-for-user user))

(defn create-token! [{:keys [user value]}]
  (-> (db/create-token! {:id-user (:id user) :value value})
      (get (keyword "last_insert_rowid()"))))

(defn give-token! [email]
  (jdbc/with-db-transaction [t-conn *db*]
    (binding [*db* t-conn]
      (when (nil? (get-user-by-email email))
        (create-user! {:email email}))
      (let [user (get-user-by-email email)
            token-value (gen-token)]
        (delete-tokens-for-user! user)
        (create-token! {:value token-value :user user})
        (send-token-by-email! email token-value)
        token-value))))
