-- :name create-user! :insert
-- :doc creates a new user record
INSERT INTO users (email) VALUES (:email)

-- :name update-user! :! :n
-- :doc updates an existing user record
UPDATE users
SET email = :email
WHERE id = :id

-- :name get-user :? :1
-- :doc retrieves a user record given the id
SELECT id, email FROM users
WHERE id = :id

-- :name get-user-by-email :? :1
SELECT id, email FROM users WHERE email = :email

-- :name delete-user! :! :n
-- :doc deletes a user record given the id
DELETE FROM users
WHERE id = :id

-- :name get-user-from-token :? :1
SELECT u.id, u.email FROM users u
JOIN tokens t ON u.id = t.id_user
WHERE t.value = :token-value

-- :name get-user-for-captured-reference :? :1
SELECT u.id, u.email
FROM users u
JOIN captured_references c
ON u.id = c.id_user
WHERE u.id == :id
