-- :snip snip-select
SELECT cr.id as id,
       cr.reference as reference,
       cr.created_at as created_at,
       CASE
            WHEN a.id IS NULL THEN FALSE ELSE TRUE
       END AS classified
FROM captured_references AS cr
LEFT OUTER JOIN articles AS a ON a.id_captured_reference = cr.id

-- :name create-captured-reference! :insert
-- :doc creates a new captured-reference
INSERT INTO captured_references
(reference, created_at)
VALUES (:reference, :created-at)

-- :name q-get-captured-reference :? :1
-- :doc retrieves a captured-reference given it'is id
:snip:select
WHERE cr.id = :id

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

-- :name delete-captured-reference! :! :n
DELETE FROM captured_references WHERE id = :id
