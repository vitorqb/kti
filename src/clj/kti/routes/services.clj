(ns kti.routes.services
  (:require [kti.routes.services.captured-references :refer :all]
            [kti.routes.services.articles
             :refer [get-all-articles get-article create-article!]]
            [ring.util.http-response :refer :all]
            [compojure.api.sweet :refer :all]
            [schema.core :as s]))

;;
;; Schemas
;; 
(s/defschema CapturedReference {:id         Integer
                                :reference  s/Str
                                :created-at java.time.LocalDateTime
                                :classified s/Bool})

(s/defschema Article {:id                    Integer
                      :id-captured-reference Integer
                      :description           s/Str
                      :action-link           (s/maybe s/Str)
                      :tags                  [s/Str]})

(s/defschema ArticleInput (dissoc Article :id))

(defmethod ring.swagger.json-schema/convert-class
  java.time.LocalDateTime [_ _] {:type "string" :format "date-time"})

;;
;; Routes
;; 
(def service-routes
  (api
    {:swagger {:ui "/swagger-ui"
               :spec "/swagger.json"
               :data {:info {:version "1.0.0"
                             :title "Keep This Info - KTI"
                             :description (concat
                                           "A small service to capture and remember"
                                           " things you want to see later")}}}}
    
    (context "/api" []

      (GET "/captured-references" []
        :return       [CapturedReference]
        :query-params []
        :summary      "Bring all captured references"
        (ok (get-all-captured-references)))

      (GET "/captured-references/:id" [id]
        :return       CapturedReference
        :query-params []
        :summary      "Get for a captured reference."
        (if-let [captured-reference (get-captured-reference id)]
          (ok captured-reference)
          (not-found)))

      (POST "/captured-references" []
        :return       CapturedReference
        :body-params  [reference :- s/Str]
        :summary      "Creates a captured reference"
        (let [id (create-captured-reference! {:reference reference})
              captured-ref (get-captured-reference id)]
          (created
           (str "/captured-references/" (:id captured-ref))
           captured-ref)))

      (PUT "/captured-references/:id" [id]
        :return       CapturedReference
        :body-params  [reference :- s/Str]
        :summary      "Put for a captured reference"
        (if-let [captured-reference (get-captured-reference id)]
          (do
            (update-captured-reference! id {:reference reference})
            (ok (get-captured-reference id)))
          (not-found)))

      (GET "/articles" []
        :return       [Article]
        :query-params []
        :summary      "Bring all articles"
        (ok (get-all-articles)))

      ;; !!!! TODO TEST POST
      (POST "/articles" []
        :return       Article
        :body         [article-data ArticleInput]
        :summary      "POST for article"
        (ok (get-article (create-article! article-data)))))))
