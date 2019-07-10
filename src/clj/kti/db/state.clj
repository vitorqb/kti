(ns kti.db.state
  (:require 
   [conman.core :as conman]
   [kti.config :refer [env]]
   [mount.core :refer [defstate]]))

(defstate ^:dynamic *db*
          :start (conman/connect! {:jdbc-url (env :database-url)})
          :stop (conman/disconnect! *db*))
