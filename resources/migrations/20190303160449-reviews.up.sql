CREATE TABLE reviews
(id INTEGER PRIMARY KEY,
 id_article INTEGER NOT NULL,
 feedback_text TEXT NOT NULL,
 status TEXT NOT NULL,
 FOREIGN KEY (id_article) REFERENCES articles(id));
