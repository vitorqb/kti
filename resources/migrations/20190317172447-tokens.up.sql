CREATE TABLE tokens (
    id INTEGER PRIMARY KEY,
    id_user INTEGER UNIQUE,
    value TEXT NOT NULL UNIQUE,
    FOREIGN KEY (id_user) REFERENCES users(id)
);
