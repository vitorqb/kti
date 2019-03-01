-- :name create-captured-reference! :insert
-- :doc creates a new captured-reference
INSERT INTO captured_references
(reference, created_at)
VALUES (:reference, :created-at)

-- :name get-captured-reference :? :1
-- :doc retrieves a captured-reference given it'is id
SELECT id, reference, created_at, FALSE as classified FROM captured_references
WHERE id = :id

-- :name delete-all-captured-references :! :n
-- :doc deletes all captured references
DELETE FROM captured_references

-- :name update-captured-reference! :! :n
UPDATE captured_references
SET reference = :reference
WHERE id = :id

-- :name get-all-captured-references :? :*
-- :doc retrieves all captured references
SELECT id, reference, created_at, FALSE as classified FROM captured_references
