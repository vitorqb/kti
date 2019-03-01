-- :name get-all-tags :? :*
-- :doc retrieves all tags
SELECT tag FROM tags

-- :name get-tags-for-article :? :*
-- :doc retrieves all tags for an article
SELECT id_tag FROM articles_tags WHERE id_article = :id

-- :name count-tags :? :1
-- :doc counts number of entries in tags table
SELECT COUNT(*) FROM tags;

-- :name create-tag! :insert
-- :doc creates a new entry on tags
INSERT INTO tags (tag) VALUES (:tag)

-- :name delete-all-tags :! :n
-- :doc deletes all tags
DELETE FROM tags
