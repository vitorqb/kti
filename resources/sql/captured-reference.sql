-- :snip snip-select
SELECT id,
       reference,
       created_at,
       ( SELECT
             CASE
                WHEN EXISTS(SELECT * FROM articles WHERE id_captured_reference = cr.id)
                     THEN True
                ELSE False
             END
       ) as classified
FROM captured_references cr

-- :name create-captured-reference! :insert
-- :doc creates a new captured-reference
INSERT INTO captured_references
(reference, created_at)
VALUES (:reference, :created-at)

-- :name q-get-captured-reference :? :1
-- :doc retrieves a captured-reference given it'is id
:snip:select
WHERE id = :id

-- :name q-get-all-captured-references :? :*
-- :doc retrieves all captured references
:snip:select

-- :name delete-all-captured-references :! :n
-- :doc deletes all captured references
DELETE FROM captured_references

-- :name update-captured-reference! :! :n
UPDATE captured_references
SET reference = :reference
WHERE id = :id

-- :name captured-reference-id-exists? :? :1
SELECT :id AS res FROM captured_references WHERE id = :id
