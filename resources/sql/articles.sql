-- :snip snip-select-article
SELECT id,
       id_captured_reference,
       description,
       action_link,
       (
         SELECT GROUP_CONCAT(a_t.id_tag, ' ')
         FROM articles_tags a_t
         WHERE a_t.id_article = a.id
       ) as tags
FROM articles a

-- :name q-get-article :? :1
:snip:select WHERE id = :id

-- :name q-get-all-articles :? :*
:snip:select

-- :name count-articles :? :1
-- :doc counts number of entries in articles table
SELECT COUNT(*) FROM articles;

-- :name article-exists? :? :1
SELECT :id AS resp FROM articles WHERE id = :id

-- :name create-article-tag! :insert
-- :doc creates a new entry on articles_tags
INSERT INTO articles_tags (id_article, id_tag) VALUES (:article-id, :tag)

-- :name create-article! :insert
-- :doc creates a new entry on articles
INSERT INTO articles (id_captured_reference, description, action_link)
VALUES (:id-captured-reference, :description, :action-link)

-- :name delete-all-articles :! :n
-- :doc deletes all articles
DELETE FROM articles

-- :name delete-all-articles-tags :! :n
-- :doc deletes all entries from articles-tags
DELETE FROM articles_tags

-- :name delete-article-tags :! :n
DELETE FROM articles_tags WHERE id_article = :id

-- :name update-article! :! :n
UPDATE articles
SET id_captured_reference = :id-captured-reference,
    description = :description,
    action_link = :action-link
WHERE id = :id
