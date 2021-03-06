(ns kti.routes.services
  (:require [kti.routes.services.captured-references :refer :all]
            [kti.routes.services.captured-references.base :refer :all]
            [kti.routes.services.articles
             :refer [get-user-articles create-article! update-article!
                     article-exists? delete-article!
                     ARTICLE_ERR_INVALID_CAPTURED_REFERENCE_ID]]
            [kti.routes.services.articles.base
             :refer [get-article-for-captured-reference get-article]]
            [kti.routes.services.reviews
             :refer [create-review! get-review get-all-reviews update-review!
                     delete-review! get-user-reviews]]
            [kti.routes.services.tokens :refer [give-token!]]
            [kti.validation :refer [kti-error?]]
            [ring.util.http-response :refer :all]
            [compojure.api.sweet :refer :all]
            [schema.core :as s]
            [clojure.core.match :refer [match]]))

;;
;; Helpers
;;
(defmacro let-found? [binding & body]
  `(if-let ~binding
     (do ~@body)
     (not-found)))

(defmacro let-auth? [binding & body]
  "Creates a context with a user from a request, or returns 401 if no user.
   Example usage:
   (let-auth? [user request] (get-something user))"
  (let [updated-binding (update binding 1 #(list :user %))]
    `(if-let ~updated-binding
       (do ~@body)
       (unauthorized))))

(defmacro match-err {:style/indent 1} [var & clauses]
  `(let [res# ~var]
     (match [(kti-error? res#) res#]
       ~@clauses)))

(defn extract-paginate-opts 
  "Creates paginate-opts from page and page-size"
  [page page-size]
  (and page (> page 0)
       page-size (> page-size 0)
       {:page page :page-size page-size}))


;;
;; Schemas
;; 

(s/defschema ReviewStatus
  (s/enum :in-progress :completed :discarded))

(s/defschema CapturedReferenceOutput
  {:id         Integer
   :reference  s/Str
   :created-at java.time.LocalDateTime
   :classified s/Bool
   :article-id (s/maybe Integer)
   :review-id  (s/maybe Integer)
   :review-status (s/maybe ReviewStatus)})

(s/defschema PaginatedCapturedReferenceOutput
  {:page s/Int
   :page-size s/Int
   :total-items s/Int
   :items [CapturedReferenceOutput]})

(s/defschema Article
  {:id                    Integer
   :id-captured-reference Integer
   :description           s/Str
   :action-link           (s/maybe s/Str)
   :tags                  #{s/Str}})

(s/defschema ArticleInput
  (dissoc Article :id))

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
               :data {:info {:version "1.6.0"
                             :title "Keep This Info - KTI"
                             :description (str "A small service to capture and"
                                               " remember things you want to see"
                                               " later")}}}}
    
    (context "/api" []

      (GET "/captured-references" []
        :return        (s/cond-pre
                        PaginatedCapturedReferenceOutput
                        [CapturedReferenceOutput])
        :header-params [authorization :- s/Str]
        :summary      "Bring all captured references"
        :query-params [{page :- s/Int nil}
                       {page-size :- s/Int 10}
                       {has-article :- s/Bool nil}
                       {has-review :- s/Bool nil}
                       :as query-params]
        (fn [r]
          (let-auth? [user r]
            (let [paginate-opts (extract-paginate-opts page page-size)
                  filter-opts (query-params->filter-opts query-params)
                  opts {:paginate-opts paginate-opts :filter-opts filter-opts}]
              (ok (get-user-captured-references user opts))))))

      (GET "/captured-references/:id" [id]
        :return       CapturedReferenceOutput
        :header-params [authorization :- s/Str]
        :summary      "Get for a captured reference."
        (fn [r]
          (let-auth? [user r]
            (let-found? [captured-reference (get-captured-reference id user)]
              (ok captured-reference)))))

      (POST "/captured-references" []
        :return        CapturedReferenceOutput
        :header-params [authorization :- s/Str]
        :body-params   [reference :- s/Str]
        :summary      "Creates a captured reference"
        (fn [r]
          (let-auth? [user r]
            (match-err (create-captured-reference! {:reference reference :user user})
              [true err] (bad-request err)
              [false id] (created
                          (str "/captured-references/" id)
                          (get-captured-reference id))))))

      (PUT "/captured-references/:id" [id]
        :return        CapturedReferenceOutput
        :header-params [authorization :- s/Str]
        :body-params   [reference :- s/Str]
        :summary       "Put for a captured reference"
        (fn [r]
          (let-auth? [user r]
            (let-found? [captured-reference (get-captured-reference id user)]
              (if-let [err (update-captured-reference! id {:reference reference})]
                (bad-request err)
                (ok (get-captured-reference id user)))))))

      (DELETE "/captured-references/:id" [id]
        :header-params [authorization :- s/Str]
        :summary       "Deletes a captured reference"
        (fn [r]
          (let-auth? [user r]
            (let-found? [captured-reference (get-captured-reference id user)]
              (if-let [err (delete-captured-reference! id)]
                (bad-request err)
                (ok []))))))

      (GET "/articles" []
        :return        [Article]
        :header-params [authorization :- s/Str]
        :summary       "Bring all articles"
        (fn [r]
          (let-auth? [user r]
            (ok (get-user-articles user)))))

      (GET "/articles/:id" [id]
        :return        Article
        :header-params [authorization :- s/Str]
        :summary       "Get for a single article"
        (fn [r]
          (let-auth? [user r]
            (let-found? [article (get-article id user)]
              (ok article)))))

      (POST "/articles" []
        :return        Article
        :header-params [authorization :- s/Str]
        :body          [{:keys [id-captured-reference] :as data} ArticleInput]
        :summary      "POST for article"
        (fn [r]
          (let-auth? [user r]
            (match-err (create-article! data user)
              [true  err] (bad-request err)
              [false id]  (created (str "/articles/" id) (get-article id))))))

      (PUT "/articles/:id" [id]
        :return        Article
        :header-params [authorization :- s/Str]
        :body          [data ArticleInput]
        :summary       "PUT for article"
        (fn [r]
          (let-auth? [user r]
            (let-found? [article (get-article id user)]
              (if-let [error (update-article! id data)]
                (bad-request error)
                (ok (get-article id)))))))

      (DELETE "/articles/:id" [id]
        :return  []
        :header-params [authorization :- s/Str]
        :summary "Deletes an article"
        (fn [r]
          (let-auth? [user r]
            (let-found? [article (get-article id user)]
              (if-let [error (delete-article! id)]
                (bad-request error)
                (ok []))))))

      (POST "/reviews" []
        :return        Review
        :header-params [authorization :- s/Str]
        :body          [data ReviewInput]
        :summary       "POST for review"
        (fn [r]
          (let-auth? [user r]
            (match-err (create-review! data user)
              [true  err] (bad-request err)
              [false id]  (created (str "/reviews/" id) (get-review id))))))

      (PUT "/reviews/:id" [id]
        :return    Review
        :header-params [authorization :- s/Str]
        :body      [data ReviewInput]
        :summary   "POST for review"
        (fn [r]
          (let-auth? [user r]
            (let-found? [_ (get-review id user)]
              (match-err (update-review! id data)
                [true err] (bad-request err)
                [false  _] (ok (get-review id)))))))

      (DELETE "/reviews/:id" [id]
        :return  []
        :header-params [authorization :- s/Str]
        :summary "Deletes a review"
        (fn [r]
          (let-auth? [user r]
            (let-found? [_ (get-review id user)]
              (delete-review! id)
              (ok [])))))

      (GET "/reviews" []
        :return        [Review]
        :header-params [authorization :- s/Str]
        :summary       "GET for reviews"
        (fn [r]
          (let-auth? [user r]
            (ok (get-user-reviews user)))))

      (GET "/reviews/:id" [id]
        :return        Review
        :header-params [authorization :- s/Str]
        :summary       "GET for a single review"
        (fn [r]
          (let-auth? [user r]
            (let-found? [review (get-review id user)]
              (ok review)))))

      (POST "/token" []
        :body-params [email :- s/Str]
        :summary     "Sends an email with a token for the user with this email."
        (do
          (give-token! email)
          (no-content))))))
