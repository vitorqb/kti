-- :snip snip-select
SELECT cr.id as id,
       cr.reference as reference,
       cr.created_at as created_at,
       a.id as article_id,
       CASE
            WHEN a.id IS NULL THEN FALSE ELSE TRUE
       END AS classified,
       r.id as review_id,
       r.status as review_status
FROM captured_references AS cr
LEFT OUTER JOIN articles AS a ON a.id_captured_reference = cr.id
LEFT OUTER JOIN reviews AS r ON r.id_article = a.id

-- :snip snip-filter
--~ (if (:filter.review-is-nil? params) "AND review_id IS NULL")
--~ (if (:filter.review-is-not-nil? params) "AND review_id IS NOT NULL")
--~ (if (:filter.article-is-nil? params) "AND article_id IS NULL")
--~ (if (:filter.article-is-not-nil? params) "AND article_id IS NOT NULL")

-- :name create-captured-reference! :insert
-- :doc creates a new captured-reference
INSERT INTO captured_references (reference, created_at, id_user)
VALUES (:reference, :created-at, :id-user)

-- :name q-get-captured-reference :? :1
-- :doc retrieves a captured-reference given it'is id
:snip:select
WHERE cr.id = :id
--~ (if (:user params) "AND id_user = :value:user.id")

-- :name q-get-user-captured-references :? :*
:snip:select
WHERE cr.id_user = :value:user.id
:snip:filter
ORDER BY cr.created_at DESC
:paginating:paginate-opts

-- :name delete-all-captured-references :! :n
-- :doc deletes all captured references
DELETE FROM captured_references

-- :name update-captured-reference! :! :n
UPDATE captured_references
SET reference = :reference
WHERE id = :id

-- :name delete-captured-reference! :! :n
DELETE FROM captured_references WHERE id = :id

-- :name q-count-user-captured-references :? :1
SELECT COUNT(*)
FROM (
    :snip:select
    WHERE id_user = :user.id
    :snip:filter
)
