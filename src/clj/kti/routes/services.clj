(ns kti.routes.services
  (:require [kti.routes.services.captured-references :refer :all]
            [kti.routes.services.captured-references.base :refer :all]
            [kti.routes.services.articles
             :refer [get-all-articles get-article create-article! update-article!
                     article-exists? delete-article!]]
            [kti.routes.services.articles.base
             :refer [get-article-for-captured-reference]]
            [kti.routes.services.reviews
             :refer [create-review! get-review get-all-reviews update-review!
                     delete-review!]]
            [ring.util.http-response :refer :all]
            [compojure.api.sweet :refer :all]
            [schema.core :as s]))

;;
;; Helpers
;;
(defmacro let-found? [binding & body]
  `(if-let ~binding
     (do ~@body)
     (not-found)))

;;
;; Schemas
;; 
(s/defschema CapturedReference
  {:id         Integer
   :reference  s/Str
   :created-at java.time.LocalDateTime
   :classified s/Bool})

(s/defschema Article
  {:id                    Integer
   :id-captured-reference Integer
   :description           s/Str
   :action-link           (s/maybe s/Str)
   :tags                  #{s/Str}})

(s/defschema ArticleInput
  (dissoc Article :id))

(s/defschema ReviewStatus
  (s/enum :in-progress :completed :discarded))

(s/defschema Review
  {:id            Integer
   :id-article    Integer
   :feedback-text s/Str
   :status         ReviewStatus})

(s/defschema ReviewInput
  (dissoc Review :id))

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
                             :description (str "A small service to capture and"
                                               " remember things you want to see"
                                               " later")}}}}
    
    (context "/api" []

      (GET "/captured-references" []
        :return       [CapturedReference]
        :summary      "Bring all captured references"
        (ok (get-all-captured-references)))

      (GET "/captured-references/:id" [id]
        :return       CapturedReference
        :summary      "Get for a captured reference."
        (let-found? [captured-reference (get-captured-reference id)]
          (ok captured-reference)))

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
        (let-found? [captured-reference (get-captured-reference id)]
          (update-captured-reference! id {:reference reference})
          (ok (get-captured-reference id))))

      (DELETE "/captured-references/:id" [id]
        :summary "Deletes a captured reference"
        (let-found? [captured-reference (get-captured-reference id)]
          (if-let [err (delete-captured-reference! id)]
            (bad-request err)
            (ok []))))

      (GET "/articles" []
        :return       [Article]
        :summary      "Bring all articles"
        (ok (get-all-articles)))

      (GET "/articles/:id" [id]
        :return  Article
        :summary "Get for a single article"
        (let-found? [article (get-article id)] (ok article)))

      (POST "/articles" []
        :return       Article
        :body         [article-data ArticleInput]
        :summary      "POST for article"
        (let [id (create-article! article-data)]
          (created
           (str "/articles/" id)
           (get-article id))))

      (PUT "/articles/:id" [id]
        :return  Article
        :body    [data ArticleInput]
        :summary "PUT for article"
        (let-found? [article (get-article id)]
          (update-article! id data)
          (ok (get-article id))))

      (DELETE "/articles/:id" [id]
        :return  []
        :summary "Deletes an article"
        (let-found? [article (get-article id)]
          (delete-article! id)
          (ok [])))

      (POST "/reviews" []
        :return        Review
        :body          [data ReviewInput]
        :summary       "POST for review"
        (let [id (create-review! data)]
          (created (str "/reviews/" id) (get-review id))))

      (PUT "/reviews/:id" [id]
        :return    Review
        :body      [data ReviewInput]
        :summary   "POST for review"
        (let-found? [_ (get-review id)]
          (update-review! id data)
          (ok (get-review id))))

      (DELETE "/reviews/:id" [id]
        :return  []
        :summary "Deletes a review"
        (let-found? [_ (get-review id)]
          (delete-review! id)
          (ok [])))

      (GET "/reviews" []
        :return        [Review]
        :summary       "GET for reviews"
        (ok (get-all-reviews)))

      (GET "/reviews/:id" [id]
        :return        Review
        :summary       "GET for a single review"
        (let-found? [review (get-review id)] (ok review))))))
