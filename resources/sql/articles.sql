-- :name get-article :? :1
-- :doc retrieves an article given it id
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
WHERE id = :id

-- :name get-all-articles :? :*
-- :doc retrieves all articles
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
