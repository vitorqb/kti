(ns kti.env
  (:require [selmer.parser :as parser]
            [clojure.tools.logging :as log]
            [kti.dev-middleware :refer [wrap-dev]]))

(def defaults
  {:init
   (fn []
     (parser/cache-off!)
     (log/info "\n-=[kti started successfully using the development profile]=-"))
   :stop
   (fn []
     (log/info "\n-=[kti has shut down successfully]=-"))
   :middleware wrap-dev})
