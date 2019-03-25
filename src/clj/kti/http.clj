(ns kti.http
  (:require [clj-http.client :as client]
            [kti.config :refer [env]]
            [clojure.tools.logging :as log]
            [selmer.parser :refer [render]]))

(def EMAIL-FROM "kti@kti.com")
(def MAILGUN-API-URL-TEMPLATE
  "https://api.mailgun.net/v3/{{domain}}/messages")

(defn send-email [addr txt]
  (if (or (env :dev) (env :test))
    (log/info (str "SEND-EMAIL CALLED WITH EMAIL = " addr " AND TEST = " txt))
    (let [url (render MAILGUN-API-URL-TEMPLATE {:domain (env :mailgun-domain)})]
      (client/post
       url
       {:basic-auth ["api" (env :mailgun-api-key)]
        :form-params {:from EMAIL-FROM
                      :to addr
                      :subject "Your KTI TOken"
                      :text txt}}))))
