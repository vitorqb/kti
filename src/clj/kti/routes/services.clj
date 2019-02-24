(ns kti.routes.services
  (:require [kti.routes.services.captured-references :refer :all]
            [ring.util.http-response :refer :all]
            [compojure.api.sweet :refer :all]
            [schema.core :as s]
            [java-time]))

;;
;; Schemas
;; 
(s/defschema CapturedReference {:id Integer
                                :reference s/Str
                                :created-at java.time.LocalDateTime
                                :classified s/Bool})

;;
;; Routes
;; 
(def service-routes
  (api
    {:swagger {:ui "/swagger-ui"
               :spec "/swagger.json"
               :data {:info {:version "1.0.0"
                             :title "Sample API"
                             :description "Sample Services"}}}}
    
    (context "/api" []
      :tags ["thingie"]

      (POST "/captured-references" []
        :return       CapturedReference
        :body-params  [reference :- s/Str]
        :summary      "Creates a captured reference"
        (let [id (create-captured-reference! {:reference reference})
              captured-ref (get-captured-reference id)]
          (created
           (str "/captured-references/" (:id captured-ref))
           captured-ref)))

      (GET "/captured-references" []
        :return       [CapturedReference]
        :query-params []
        :summary      "Bring all captured references"
        (ok (into [] (get-all-captured-references))))

      (GET "/captured-references/:id" [id]
        :return       CapturedReference
        :query-params []
        :summary      "Get for a captured reference."
        (if-let [captured-reference (get-captured-reference id)]
          (ok captured-reference)
          (not-found)))
      
      (GET "/plus" []
        :return       Long
        :query-params [x :- Long, {y :- Long 1}]
        :summary      "x+y with query-parameters. y defaults to 1."
        (ok (+ x y)))

      (POST "/minus" []
        :return      Long
        :body-params [x :- Long, y :- Long]
        :summary     "x-y with body-parameters."
        (ok (- x y)))

      (GET "/times/:x/:y" []
        :return      Long
        :path-params [x :- Long, y :- Long]
        :summary     "x*y with path-parameters"
        (ok (* x y)))

      (POST "/divide" []
        :return      Double
        :form-params [x :- Long, y :- Long]
        :summary     "x/y with form-parameters"
        (ok (/ x y)))

      (GET "/power" []
        :return      Long
        :header-params [x :- Long, y :- Long]
        :summary     "x^y with header-parameters"
        (ok (long (Math/pow x y)))))))
