(ns user
  (:require [kti.config :refer [env]]
            [clojure.spec.alpha :as s]
            [expound.alpha :as expound]
            [mount.core :as mount]
            [kti.core :refer [start-app]]
            [kti.db.core]
            [kti.routes.services.tokens :as tokens]
            [conman.core :as conman]
            [luminus-migrations.core :as migrations]))

(alter-var-root #'s/*explain-out* (constantly expound/printer))

(defn start []
  (mount/start-without #'kti.core/repl-server))

(defn stop []
  (mount/stop-except #'kti.core/repl-server))

(defn restart []
  (stop)
  (start))

(defn restart-db []
  (mount/stop #'kti.db.core/*db*)
  (mount/start #'kti.db.core/*db*)
  (binding [*ns* 'kti.db.core]
    (conman/bind-connection kti.db.core/*db* "sql/queries.sql")))

(defn reset-db []
  (migrations/migrate ["reset"] (select-keys env [:database-url])))

(defn migrate []
  (migrations/migrate ["migrate"] (select-keys env [:database-url])))

(defn rollback []
  (migrations/migrate ["rollback"] (select-keys env [:database-url])))

(defn create-migration [name]
  (migrations/create name (select-keys env [:database-url])))

(defn give-token-to-test-user! []
  (tokens/give-token! "test@test.test"))
