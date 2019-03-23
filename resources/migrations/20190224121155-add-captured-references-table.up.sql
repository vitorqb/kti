CREATE TABLE captured_references
(id INTEGER PRIMARY KEY,
 reference TEXT NOT NULL,
 created_at DATETIME NOT NULL,
 id_user INTEGER,
 FOREIGN KEY (id_user) REFERENCES users(id));
