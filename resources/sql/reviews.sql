-- :name get-review :? :1
-- :doc retrieves an review given an id
SELECT id, id_article, feedback_text, status FROM reviews WHERE id = :id

-- :name create-review! :insert
INSERT INTO reviews (id_article, feedback_text, status)
VALUES (:id-article, :feedback-text, :status)
