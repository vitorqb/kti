-- :name get-review :? :1
-- :doc retrieves an review given an id
SELECT r.id, r.id_article, r.feedback_text, r.status
FROM reviews r
--~ (if (:user params) "JOIN articles a ON a.id = r.id_article")
--~ (if (:user params) "JOIN captured_references c ON c.id = a.id_captured_reference")
WHERE r.id = :id
--~ (if (:user params) "AND c.id_user = :value:user.id")

-- :name get-review-for-article :? :1
SELECT id, id_article, feedback_text, status FROM reviews WHERE id_article = :id

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
