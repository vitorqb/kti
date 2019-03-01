CREATE TABLE tags (tag TEXT PRIMARY KEY);
--;;
CREATE TABLE articles
(id INTEGER PRIMARY KEY,
 id_captured_reference INTEGER NOT NULL,
 description TEXT NOT NULL,
 action_link TEXT,
 FOREIGN KEY (id_captured_reference) REFERENCES captured_references(id));
--;;
CREATE TABLE articles_tags
(id INTEGER PRIMARY KEY,
 id_article INTEGER NOT NULL,
 id_tag TEXT NOT NULL,
 FOREIGN KEY (id_article) REFERENCES article(id),
 FOREIGN KEY (id_tag) REFERENCES tags(tag));
