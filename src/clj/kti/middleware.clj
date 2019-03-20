(ns kti.middleware
  (:require [kti.env :refer [defaults]]
            [clojure.string :as str]
            [kti.config :refer [env]]
            [kti.routes.services.users :refer [get-user-from-token]]
            [ring.middleware.flash :refer [wrap-flash]]
            [ring.util.response :refer [get-header]]
            [immutant.web.middleware :refer [wrap-session]]
            [ring.middleware.defaults :refer [site-defaults wrap-defaults]]))

(defn extract-token [request]
  (some-> request
          (get-header "authorization")
          str/lower-case
          str/trim
          (#(and (re-matches #"^token [^ ]+$" %) %))
          (->> (drop 6) (apply str))))

(defn bind-user [handler extract-user]
  (fn [request]
    (let [user (extract-user request)]
      (handler (if user (assoc request :user user) request)))))

(defn wrap-base [handler]
  (-> ((:middleware defaults) handler)
      wrap-flash
      (wrap-session {:cookie-attrs {:http-only true}})
      (wrap-defaults
        (-> site-defaults
            (assoc-in [:security :anti-forgery] false)
            (dissoc :session)))
      (bind-user #(some-> % extract-token get-user-from-token))))
