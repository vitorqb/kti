-- :name get-review :? :1
-- :doc retrieves an review given an id
SELECT id, id_article, feedback_text, status FROM reviews WHERE id = :id

-- :name create-review! :insert
INSERT INTO reviews (id_article, feedback_text, status)
VALUES (:id-article, :feedback-text, :status)

-- :name get-all-reviews :? :*
SELECT id, id_article, feedback_text, status FROM reviews

-- :name delete-all-reviews :!
DELETE FROM reviews

-- :name update-review! :!
UPDATE reviews
SET id_article = :id-article,
    feedback_text = :feedback-text,
    status = :status
WHERE id = :id

-- :name delete-review! :!
DELETE FROM reviews WHERE id = :id
